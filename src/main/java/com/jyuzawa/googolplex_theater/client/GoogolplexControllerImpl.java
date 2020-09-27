package com.jyuzawa.googolplex_theater.client;

import com.jyuzawa.googolplex_theater.config.CastConfig;
import com.jyuzawa.googolplex_theater.config.CastConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.protobuf.CastMessages.CastMessage;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class represents the state of the application. All modifications to state occur in the same
 * thread.
 *
 * @author jyuzawa
 */
public final class GoogolplexControllerImpl implements GoogolplexController {
  private static final Logger LOG = LoggerFactory.getLogger(GoogolplexControllerImpl.class);

  private static final int CONNECT_TIMEOUT_MILLIS = 5000;

  private static final int DEFAULT_BASE_RECONNECT_SECONDS = 15;
  private static final int DEFAULT_RECONNECT_NOISE_SECONDS = 5;
  private static final int RECONNECT_EXPONENTIAL_BACKOFF_MULTIPLIER = 2;

  private final EventLoopGroup eventLoopGroup;
  private final EventLoop eventLoop;
  private final Bootstrap bootstrap;
  private final Map<String, DeviceInfo> nameToDeviceInfo;
  private final Map<String, String> serviceNameToName;
  private final Map<String, InetSocketAddress> nameToAddress;
  private final Map<String, Channel> nameToChannel;
  private final Map<String, Integer> nameToBackoffSeconds;
  private final int baseReconnectSeconds;
  private final int reconnectNoiseSeconds;

  public GoogolplexControllerImpl(EventLoopGroup eventLoopGroup, String appId) throws IOException {
    this(eventLoopGroup, appId, DEFAULT_BASE_RECONNECT_SECONDS, DEFAULT_RECONNECT_NOISE_SECONDS);
  }

  public GoogolplexControllerImpl(
      EventLoopGroup eventLoopGroup,
      String appId,
      int baseReconnectSeconds,
      int reconnectNoiseSeconds)
      throws IOException {
    // the state is maintained in these maps
    this.nameToDeviceInfo = new ConcurrentHashMap<>();
    this.serviceNameToName = new ConcurrentHashMap<>();
    this.nameToAddress = new ConcurrentHashMap<>();
    this.nameToChannel = new ConcurrentHashMap<>();
    this.nameToBackoffSeconds = new ConcurrentHashMap<>();
    this.baseReconnectSeconds = baseReconnectSeconds;
    this.reconnectNoiseSeconds = reconnectNoiseSeconds;
    this.eventLoopGroup = eventLoopGroup;
    this.eventLoop = eventLoopGroup.next();
    SslContext sslContext =
        SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
    LOG.info("Using cast application id: {}", appId);
    // configure the socket client
    this.bootstrap =
        new Bootstrap()
            .channel(NioSocketChannel.class)
            .group(eventLoopGroup)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS);
    // the pipeline to use for the socket client
    this.bootstrap.handler(
        new ChannelInitializer<SocketChannel>() {
          @Override
          public void initChannel(SocketChannel ch) throws Exception {
            ChannelPipeline p = ch.pipeline();
            p.addLast("ssl", sslContext.newHandler(ch.alloc()));
            p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
            p.addLast("protobufDecoder", new ProtobufDecoder(CastMessage.getDefaultInstance()));
            p.addLast("frameEncoder", new LengthFieldPrepender(4));
            p.addLast("protobufEncoder", new ProtobufEncoder());
            p.addLast("logger", new LoggingHandler());
            p.addLast("handler", new GoogolplexClientHandler(appId));
          }
        });
  }

  public EventLoopGroup getEventLoopGroup() {
    return eventLoopGroup;
  }

  /**
   * Load the config and propagate the changes to the any currently connected devices.
   *
   * @param config the settings loaded from the file
   */
  @Override
  public void accept(CastConfig config) {
    try {
      eventLoop
          .submit(
              () -> {
                Set<String> namesToRemove = new HashSet<>(nameToDeviceInfo.keySet());
                for (DeviceInfo deviceInfo : config.devices) {
                  String name = deviceInfo.name;
                  // mark that we should not remove this device
                  namesToRemove.remove(name);
                  DeviceInfo oldDeviceInfo = nameToDeviceInfo.get(name);
                  // ignore unchanged devices
                  if (!deviceInfo.equals(oldDeviceInfo)) {
                    LOG.info("CONFIG_UPDATED '{}'", name);
                    nameToDeviceInfo.put(name, deviceInfo);
                    apply(name);
                  }
                }
                // remove devices that were missing in the new config
                for (String name : namesToRemove) {
                  LOG.info("CONFIG_REMOVED '{}'", name);
                  nameToDeviceInfo.remove(name);
                  apply(name);
                }
              })
          .get();
    } catch (Exception e) {
      LOG.error("Failed to load config", e);
    }
  }

  /**
   * Add a discovered device and initialize a new connection to the device if one does not exist
   * already.
   *
   * @param event mdns info
   * @throws ExecutionException
   * @throws InterruptedException
   */
  @Override
  public void register(ServiceEvent event) {
    try {
      eventLoop
          .submit(
              () -> {
                // the device information may not be full
                ServiceInfo info = event.getInfo();
                String name = info.getPropertyString("fn");
                if (name == null) {
                  LOG.debug("Found unnamed cast:\n{}", info);
                  return;
                }
                String serviceName = event.getName();
                serviceNameToName.put(serviceName, name);
                InetAddress[] addresses = info.getInetAddresses();
                if (addresses == null || addresses.length == 0) {
                  LOG.debug("Found unaddressable cast:\n{}", info);
                  return;
                }
                /*
                 * we choose the first address. there should usually be just one. the mdns library returns ipv4
                 * addresses before ipv6.
                 */
                InetSocketAddress address = new InetSocketAddress(addresses[0], info.getPort());
                InetSocketAddress oldAddress = nameToAddress.put(name, address);
                if (!address.equals(oldAddress)) {
                  /*
                   * this is a newly discovered device, or an existing device whose address was updated.
                   */
                  LOG.info("REGISTER '{}' {}", name, address);
                  apply(name);
                }
              })
          .get();
    } catch (Exception e) {
      LOG.error("Failed to register cast", e);
    }
  }

  /**
   * Apply changes to a device. This closes any existing connections. If there were no existing
   * connections, then a new connection is made. The new connection will call this function again
   * when it is closed. This logic allows for new connections to take on the proper settings.
   *
   * @param name device's name
   */
  private void apply(String name) {
    Channel oldChannel = nameToChannel.get(name);
    if (oldChannel != null) {
      LOG.info("DISCONNECT '{}'", name);
      /*
       * kill the channel, so it will reconnect and this method will be called again, but skip this code path.
       */
      safeClose(oldChannel);
      return;
    }
    // ensure that there is enough information to connect
    InetSocketAddress address = nameToAddress.get(name);
    if (address == null) {
      return;
    }
    DeviceInfo deviceInfo = nameToDeviceInfo.get(name);
    if (deviceInfo == null) {
      return;
    }
    LOG.info("CONNECT '{}'", name);
    Channel channel =
        bootstrap
            .connect(address)
            .addListener(
                (ChannelFuture f) -> {
                  /*
                   * NOTE: this callback is not executed from the controller's eventLoop, so we must use eventLoop.execute and
                   * eventLoop.schedule to ensure concurrency.
                   */
                  Consumer<Integer> reconnection =
                      (retrySeconds) -> {
                        // schedule a reconnection in a certain amount of seconds in the future
                        LOG.info("RECONNECTING '{}' in {}s", name, retrySeconds);
                        eventLoop.schedule(
                            () -> {
                              nameToChannel.remove(name);
                              apply(name);
                            },
                            retrySeconds,
                            TimeUnit.SECONDS);
                      };
                  if (f.isSuccess()) {
                    LOG.info("CONNECTED '{}'", name);
                    Channel ch = f.channel();
                    // inform the handler what the device settings are
                    ch.attr(GoogolplexClientHandler.DEVICE_INFO_KEY).set(deviceInfo);
                    ch.attr(GoogolplexClientHandler.CONNECTION_BIRTH_KEY).set(Instant.now());
                    /*
                     * this is what causes the persistence when the handler detects failure. additionally, it allows for the
                     * first part of this method to work properly. in that case, we are closing the connection on purpose.
                     */
                    ch.closeFuture()
                        .addListener(
                            (ChannelFuture closeFuture) -> {
                              LOG.info("DISCONNECTED '{}'", name);
                              Boolean reload =
                                  closeFuture
                                      .channel()
                                      .attr(GoogolplexClientHandler.RELOAD_KEY)
                                      .get();
                              reconnection.accept(getRetrySeconds(reload, name));
                            });
                  } else {
                    Throwable cause = f.cause();
                    LOG.error(
                        "CONNECTION_FAILURE '{}' {}: {}",
                        name,
                        cause.getClass().getSimpleName(),
                        cause.getMessage());
                    reconnection.accept(getRetrySeconds(null, name));
                  }
                })
            .channel();
    nameToChannel.put(name, channel);
  }

  /**
   * This closes a connection to a device and causes it to reconnect immediately.
   *
   * @param channel
   * @return
   */
  private void safeClose(Channel channel) {
    if (channel != null) {
      // mark the reload key, so the connection will roll over immediately
      channel.attr(GoogolplexClientHandler.RELOAD_KEY).set(Boolean.TRUE);
      channel.close();
    }
  }

  /**
   * Calculate a retry time, based on an exponential backoff
   *
   * @param reload whether an immediate reload should occur
   * @param name device name
   * @return seconds
   */
  private Integer getRetrySeconds(Boolean reload, String name) {
    if (reload != null) {
      if (reload) {
        // the channel should reload immediately
        return 0;
      }
      // the channel was up and running, so reset the backoff
      nameToBackoffSeconds.remove(name);
    }
    // retry based on an exponential backoff
    int backoffSeconds =
        nameToBackoffSeconds.compute(
            name,
            (k, v) -> {
              if (v == null) {
                return baseReconnectSeconds;
              }
              return v * RECONNECT_EXPONENTIAL_BACKOFF_MULTIPLIER;
            });
    if (reconnectNoiseSeconds > 0) {
      backoffSeconds += ThreadLocalRandom.current().nextInt(reconnectNoiseSeconds);
    }
    return backoffSeconds;
  }

  /**
   * Trigger a refresh by closing channels which will cause a reconnect.
   *
   * @param name the device to refresh
   */
  @Override
  public void refresh(String name) {
    // closing channels will cause them to reconnect
    if (name == null) {
      // close all channels
      for (Channel channel : nameToChannel.values()) {
        safeClose(channel);
      }
    } else {
      // close specific channel
      Channel channel = nameToChannel.get(name);
      safeClose(channel);
    }
  }

  @Override
  public List<JsonObject> getDeviceInfo() {
    List<JsonObject> out = new ArrayList<>();
    Set<String> allNames = new TreeSet<>();
    allNames.addAll(nameToDeviceInfo.keySet());
    allNames.addAll(nameToAddress.keySet());
    Instant now = Instant.now();
    for (String name : allNames) {
      JsonObject device = new JsonObject();
      device.put("name", name);
      DeviceInfo deviceInfo = nameToDeviceInfo.get(name);
      if (deviceInfo != null) {
        device.put("settings", deviceInfo.settings.toPrettyString());
      }
      InetSocketAddress ipAddress = nameToAddress.get(name);
      if (ipAddress != null) {
        device.put("ipAddress", ipAddress.getAddress().getHostAddress());
      }
      Channel channel = nameToChannel.get(name);
      if (channel != null) {
        Instant birth = channel.attr(GoogolplexClientHandler.CONNECTION_BIRTH_KEY).get();
        if (birth != null) {
          String duration = calculateDuration(Duration.between(birth, now));
          device.put("duration", duration);
        }
      }
      out.add(device);
    }
    return out;
  }

  /**
   * Generate a human readable connection age string.
   *
   * @param duration
   * @return
   */
  static String calculateDuration(Duration duration) {
    StringBuilder out = new StringBuilder();
    long deltaSeconds = duration.getSeconds();
    long seconds = deltaSeconds;
    long days = seconds / 86400L;
    if (deltaSeconds >= 86400L) {
      out.append(days).append("d");
    }
    seconds %= 86400L;

    long hours = seconds / 3600L;
    if (deltaSeconds >= 3600L) {
      out.append(hours).append("h");
    }
    seconds %= 3600L;

    long minutes = seconds / 60L;
    if (deltaSeconds >= 60L) {
      out.append(minutes).append("m");
    }
    seconds %= 60L;

    out.append(seconds).append("s");
    return out.toString();
  }
}
