package com.jyuzawa.googolplex_theater.client;

import com.jyuzawa.googolplex_theater.config.CastConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.protobuf.CastMessages.CastMessage;
import com.jyuzawa.googolplex_theater.protobuf.CastMessages.CastMessage.PayloadType;
import com.jyuzawa.googolplex_theater.protobuf.CastMessages.CastMessage.ProtocolVersion;
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

public final class GoogolplexHandler extends SimpleChannelInboundHandler<CastMessage> {

  private static final Logger LOG = LoggerFactory.getLogger(GoogolplexHandler.class);

  public static final AttributeKey<DeviceInfo> DEVICE_INFO_KEY =
      AttributeKey.valueOf(GoogolplexHandler.class.getCanonicalName());

  private static final String DEFAULT_RECEIVER_ID = "receiver-0";
  private static final int HEARTBEAT_SECONDS = 5;
  private static final int HEARTBEAT_TIMEOUT_SECONDS = 30;

  private static final String NAMESPACE_CUSTOM = "urn:x-cast:com.url.cast";
  private static final String NAMESPACE_CONNECTION = "urn:x-cast:com.google.cast.tp.connection";
  private static final String NAMESPACE_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat";
  private static final String NAMESPACE_RECEIVER = "urn:x-cast:com.google.cast.receiver";

  private static final Map<String, Object> CONNECT_MESSAGE = messagePayload("CONNECT");
  private static final Map<String, Object> PING_MESSAGE = messagePayload("PING");

  private final String appId;
  private final String senderId;
  private String sessionReceiverId;
  private int requestId;

  private ScheduledFuture<?> heartbeatFuture;
  private Instant lastHeartbeat;
  private final CastMessage heartbeatMessage;

  public GoogolplexHandler(String appId) throws IOException {
    this.appId = appId;
    this.senderId = "sender-" + System.identityHashCode(this);
    this.heartbeatMessage = generateMessage(NAMESPACE_HEARTBEAT, DEFAULT_RECEIVER_ID, PING_MESSAGE);
    this.lastHeartbeat = Instant.now();
  }

  private static final Map<String, Object> messagePayload(String type) {
    Map<String, Object> out = new HashMap<>();
    out.put("type", type);
    return Collections.unmodifiableMap(out);
  }

  private CastMessage generateMessage(
      String namespace, String destinationId, Map<String, Object> payload) throws IOException {
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
                    // the last heartbeat occurred too long ago, so kill the connection
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
  }

  private DeviceInfo getDeviceInfo(ChannelHandlerContext ctx) {
    return ctx.channel().attr(DEVICE_INFO_KEY).get();
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    if (heartbeatFuture != null) {
      heartbeatFuture.cancel(false);
    }
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, CastMessage msg) throws Exception {
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
      default:
        LOG.debug("other message");
        break;
    }
    if (namespace.equals(NAMESPACE_HEARTBEAT)) {
      lastHeartbeat = Instant.now();
      return;
    }
  }

  private void handleReceiverMessage(ChannelHandlerContext ctx, CastMessage msg, DeviceInfo device)
      throws IOException {
    ReceiverResponse receiverPayload =
        JsonUtil.MAPPER.readValue(msg.getPayloadUtf8(), ReceiverResponse.class);
    String name = device.name;
    if (receiverPayload.reason != null) {
      LOG.warn("ERROR '{}' {}", name, msg.getPayloadUtf8());
      return;
    }
    if (!receiverPayload.isApplicationStatus()) {
      return;
    }
    if (receiverPayload.isIdleScreen()) {
      LOG.info("DOWN '{}'", name);
      ctx.channel().close();
      return;
    }
    String transportId = receiverPayload.getApplicationTransportId(appId);
    if (sessionReceiverId == null && transportId != null) {
      sessionReceiverId = transportId;
      LOG.info("UP '{}'", name);
      // session connect
      CastMessage sessionConnectMessage =
          generateMessage(NAMESPACE_CONNECTION, transportId, CONNECT_MESSAGE);
      ctx.writeAndFlush(sessionConnectMessage);
      // display data custom message
      Map<String, Object> custom = new HashMap<>();
      custom.put("device", device);
      custom.put("requestId", requestId++);
      CastMessage customMessage = generateMessage(NAMESPACE_CUSTOM, transportId, custom);
      ctx.writeAndFlush(customMessage);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    DeviceInfo device = getDeviceInfo(ctx);
    String name = "unknown";
    if (device != null) {
      name = device.name;
    }
    LOG.error("EXCEPTION '{}' {}: {}", name, cause.getClass().getSimpleName(), cause.getMessage());
    ctx.close();
  }
}
