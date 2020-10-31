package com.jyuzawa.googolplex_theater.client;

import com.jyuzawa.googolplex_theater.config.CastConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.protobuf.Wire.CastMessage;
import com.jyuzawa.googolplex_theater.protobuf.Wire.CastMessage.PayloadType;
import com.jyuzawa.googolplex_theater.protobuf.Wire.CastMessage.ProtocolVersion;
import com.jyuzawa.googolplex_theater.util.JsonUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ScheduledFuture;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class handles messages from the device and prepares proper responses. The lifecycle is very
 * simple. Once the connection is established, the controller does not send additional messages.
 * Theoretically it could, but for simplicity of lifecycle management, we do not. If the controller
 * wants to do something after the connection is established, it will close the connection and start
 * anew. Recall that any close we trigger in this handler will cause the controller to reconnect.
 *
 * @author jyuzawa
 */
public final class GoogolplexClientHandler extends SimpleChannelInboundHandler<CastMessage> {

  private static final Logger LOG = LoggerFactory.getLogger(GoogolplexClientHandler.class);

  public static final AttributeKey<DeviceInfo> DEVICE_INFO_KEY =
      AttributeKey.valueOf(GoogolplexClientHandler.class.getCanonicalName() + "_device");
  public static final AttributeKey<Instant> CONNECTION_BIRTH_KEY =
      AttributeKey.valueOf(GoogolplexClientHandler.class.getCanonicalName() + "_birth");
  public static final AttributeKey<Boolean> RELOAD_KEY =
      AttributeKey.valueOf(GoogolplexClientHandler.class.getCanonicalName() + "_reload");

  /**
   * This is a published application for public use. The URL is
   * https://www.jyuzawa.com/googolplex-theater/receiver/index.html
   */
  public static final String DEFAULT_APPLICATION_ID = "B1A3B99B";

  static final String DEFAULT_RECEIVER_ID = "receiver-0";
  private static final int HEARTBEAT_SECONDS = 5;
  private static final int HEARTBEAT_TIMEOUT_SECONDS = 30;

  /** This custom namespace is used to identify messages related to our application. */
  static final String NAMESPACE_CUSTOM = "urn:x-cast:com.jyuzawa.googolplex-theater.device";

  static final String NAMESPACE_CONNECTION = "urn:x-cast:com.google.cast.tp.connection";
  static final String NAMESPACE_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat";
  static final String NAMESPACE_RECEIVER = "urn:x-cast:com.google.cast.receiver";

  private static final Map<String, Object> CONNECT_MESSAGE = messagePayload("CONNECT");
  private static final Map<String, Object> PING_MESSAGE = messagePayload("PING");

  private final String appId;
  private final String senderId;
  private String sessionReceiverId;
  private int requestId;

  private ScheduledFuture<?> heartbeatFuture;
  private Instant lastHeartbeat;
  private final CastMessage heartbeatMessage;

  public GoogolplexClientHandler(String appId) throws IOException {
    this.appId = appId;
    this.senderId = "sender-" + System.identityHashCode(this);
    this.heartbeatMessage = generateMessage(NAMESPACE_HEARTBEAT, DEFAULT_RECEIVER_ID, PING_MESSAGE);
  }

  /**
   * @param type
   * @return a simple message with no fields
   */
  static final Map<String, Object> messagePayload(String type) {
    Map<String, Object> out = new HashMap<>();
    out.put("type", type);
    return Collections.unmodifiableMap(out);
  }

  /**
   * Generates a protobuf message with the given payload.
   *
   * @param namespace the label to determine which message stream this belongs to
   * @param destinationId either the default value or the value established for the session
   * @param payload a series of key values to turn into a JSON string
   * @return a fully constructed message
   * @throws IOException when JSON serialization fails
   */
  private CastMessage generateMessage(
      String namespace, String destinationId, Map<String, Object> payload) throws IOException {
    return generateMessage(namespace, senderId, destinationId, payload);
  }

  /**
   * Generates a protobuf message with the given payload.
   *
   * @param namespace the label to determine which message stream this belongs to
   * @param senderId the sender
   * @param destinationId either the default value or the value established for the session
   * @param payload a series of key values to turn into a JSON string
   * @return a fully constructed message
   * @throws IOException when JSON serialization fails
   */
  static CastMessage generateMessage(
      String namespace, String senderId, String destinationId, Object payload) throws IOException {
    CastMessage.Builder out = CastMessage.newBuilder();
    out.setDestinationId(destinationId);
    out.setSourceId(senderId);
    out.setNamespace(namespace);
    out.setProtocolVersion(ProtocolVersion.CASTV2_1_0);
    out.setPayloadType(PayloadType.STRING);
    out.setPayloadUtf8(JsonUtil.MAPPER.writeValueAsString(payload));
    return out.build();
  }

  @Override
  /**
   * The connection is up. Configures keep alive messages and timeout. Sends an initial connect
   * message and launch message. The device will respond back with a receiver status message (or
   * error if the receiver could not be started). That response and the keep alive responses from
   * the device are all handled in the channelRead0().
   */
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    // initial connect
    CastMessage initialConnectMessage =
        generateMessage(NAMESPACE_CONNECTION, DEFAULT_RECEIVER_ID, CONNECT_MESSAGE);
    ctx.writeAndFlush(initialConnectMessage);

    // launch
    Map<String, Object> launch = new HashMap<>();
    launch.put("type", "LAUNCH");
    launch.put("appId", appId);
    launch.put("requestId", requestId++);
    CastMessage launchMessage = generateMessage(NAMESPACE_RECEIVER, DEFAULT_RECEIVER_ID, launch);
    ctx.writeAndFlush(launchMessage);

    // keepalive
    heartbeatFuture =
        ctx.executor()
            .scheduleWithFixedDelay(
                () -> {
                  if (lastHeartbeat
                      .plus(HEARTBEAT_TIMEOUT_SECONDS, ChronoUnit.SECONDS)
                      .isBefore(Instant.now())) {
                    /* the last heartbeat occurred too long ago, so close to trigger a reconnect */
                    String name = getDeviceInfo(ctx).name;
                    LOG.warn("EXPIRE '{}'", name);
                    ctx.close();
                  } else {
                    // send another heartbeat
                    ctx.writeAndFlush(heartbeatMessage);
                  }
                },
                HEARTBEAT_SECONDS,
                HEARTBEAT_SECONDS,
                TimeUnit.SECONDS);
    /*
     * there was no heartbeat now, but we initialize with the start time, so we don't timeout immediately.
     */
    this.lastHeartbeat = Instant.now();
  }

  /**
   * The device info has been stashed in the attribute map.
   *
   * @param ctx
   * @return the device info
   */
  private DeviceInfo getDeviceInfo(ChannelHandlerContext ctx) {
    return ctx.channel().attr(DEVICE_INFO_KEY).get();
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    // shutdown heartbeat
    if (heartbeatFuture != null) {
      heartbeatFuture.cancel(false);
    }
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, CastMessage msg) throws Exception {
    // do some rudimentary validation
    if (msg.getProtocolVersion() != ProtocolVersion.CASTV2_1_0
        || msg.getPayloadType() != PayloadType.STRING) {
      LOG.debug("Invalid message");
      return;
    }
    String sourceId = msg.getSourceId();
    if (!(sourceId.equals(DEFAULT_RECEIVER_ID) || sourceId.equals(sessionReceiverId))) {
      LOG.debug("Invalid message source");
      return;
    }
    String destinationId = msg.getDestinationId();
    if (!(destinationId.equals(senderId) || destinationId.equals("*"))) {
      LOG.debug("Invalid message destination");
      return;
    }
    // handle different namespaces differently
    String namespace = msg.getNamespace();
    DeviceInfo device = getDeviceInfo(ctx);
    String name = device.name;
    switch (namespace) {
      case NAMESPACE_HEARTBEAT:
        lastHeartbeat = Instant.now();
        break;
      case NAMESPACE_RECEIVER:
        handleReceiverMessage(ctx, msg, device);
        break;
      case NAMESPACE_CUSTOM:
        LOG.info("MESSAGE '{}' {}", name, msg.getPayloadUtf8());
        break;
      default:
        LOG.debug("other message");
        break;
    }
  }

  /**
   * Determine how to respond or act upon receiving a response from the receiver.
   *
   * @param ctx channel context
   * @param msg a receiver message
   * @param device the device name and settings
   * @throws IOException when the JSON serialization fails
   */
  private void handleReceiverMessage(ChannelHandlerContext ctx, CastMessage msg, DeviceInfo device)
      throws IOException {
    ReceiverResponse receiverPayload =
        JsonUtil.MAPPER.readValue(msg.getPayloadUtf8(), ReceiverResponse.class);
    String name = device.name;
    if (receiverPayload.reason != null) {
      // the presence of the reason indicates the launch likely failed for some reason
      LOG.warn("ERROR '{}' {}", name, msg.getPayloadUtf8());
      // close to reload connection
      ctx.close();
      return;
    }
    if (!receiverPayload.isApplicationStatus()) {
      return;
    }
    if (receiverPayload.isIdleScreen()) {
      /*
       * if the idle screen is back, the receiver app has crashed for some reason, so close which will trigger a
       * refresh.
       */
      LOG.info("DOWN '{}'", name);
      ctx.close();
      return;
    }
    String transportId = receiverPayload.getApplicationTransportId(appId);
    /*
     * if transportId is present for our appId, then we can send the settings thru our custom namespace
     */
    if (sessionReceiverId == null && transportId != null) {
      sessionReceiverId = transportId;
      LOG.info("UP '{}'", name);
      // session connect
      CastMessage sessionConnectMessage =
          generateMessage(NAMESPACE_CONNECTION, transportId, CONNECT_MESSAGE);
      ctx.writeAndFlush(sessionConnectMessage);
      // display data custom message
      Map<String, Object> custom = new HashMap<>();
      custom.put("name", device.name);
      custom.put("settings", device.settings);
      custom.put("requestId", requestId++);
      CastMessage customMessage = generateMessage(NAMESPACE_CUSTOM, transportId, custom);
      ctx.writeAndFlush(customMessage);
      ctx.channel().attr(RELOAD_KEY).set(Boolean.FALSE);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    // empirically, these are connection resets.
    DeviceInfo device = getDeviceInfo(ctx);
    String name = "unknown";
    if (device != null) {
      name = device.name;
    }
    LOG.error("EXCEPTION '{}' {}: {}", name, cause.getClass().getSimpleName(), cause.getMessage());
    // this close will trigger the controller to reconnect
    ctx.close();
  }
}
