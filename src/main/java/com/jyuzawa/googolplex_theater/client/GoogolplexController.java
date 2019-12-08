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
import io.netty.channel.nio.NioEventLoopGroup;
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
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GoogolplexController implements Closeable {
  private static final Logger LOG = LoggerFactory.getLogger(GoogolplexController.class);

  private static final int CONNECT_TIMEOUT_MILLIS = 5000;
  private static final int CONNECTION_RETRY_SECONDS = 60;

  private final EventLoopGroup eventLoopGroup;
  private final EventLoop eventLoop;
  private final Bootstrap bootstrap;
  private final Map<String, DeviceInfo> nameToDeviceInfo;
  private final Map<String, String> serviceNameToName;
  private final Map<String, InetSocketAddress> nameToAddress;
  private final Map<String, Channel> nameToChannel;

  public GoogolplexController(String appId) throws IOException {
    this.nameToDeviceInfo = new ConcurrentHashMap<>();
    this.serviceNameToName = new ConcurrentHashMap<>();
    this.nameToAddress = new ConcurrentHashMap<>();
    this.nameToChannel = new ConcurrentHashMap<>();
    this.eventLoopGroup = new NioEventLoopGroup();
    this.eventLoop = eventLoopGroup.next();
    SslContext sslContext =
        SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
    LOG.info("Using cast application id: {}", appId);
    this.bootstrap =
        new Bootstrap()
            .channel(NioSocketChannel.class)
            .group(eventLoopGroup)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS);
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
            p.addLast("handler", new GoogolplexHandler(appId));
          }
        });
  }

  public void loadConfig(CastConfig config) {
    eventLoop.execute(
        () -> {
          Set<String> namesToRemove = new HashSet<>(nameToDeviceInfo.keySet());
          for (DeviceInfo deviceInfo : config.devices) {
            String name = deviceInfo.name;
            namesToRemove.remove(name);
            DeviceInfo oldDeviceInfo = nameToDeviceInfo.get(name);
            if (!deviceInfo.equals(oldDeviceInfo)) {
              LOG.info("CONFIG_UPDATED '{}'", name);
              nameToDeviceInfo.put(name, deviceInfo);
              open(name);
            }
          }
          for (String name : namesToRemove) {
            LOG.info("CONFIG_REMOVED '{}'", name);
            nameToDeviceInfo.remove(name);
            open(name);
          }
        });
  }

  public void register(ServiceEvent event) {
    eventLoop.execute(
        () -> {
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
          InetSocketAddress address = new InetSocketAddress(addresses[0], info.getPort());
          InetSocketAddress oldAddress = nameToAddress.put(name, address);
          if (!address.equals(oldAddress)) {
            LOG.info("REGISTER '{}' {}", name, address);
            open(name);
          }
        });
  }

  private void open(String name) {
    Channel oldChannel = nameToChannel.get(name);
    if (oldChannel != null) {
      LOG.info("DISCONNECT '{}'", name);
      // kill the channel, so it will roll over
      oldChannel.close();
      return;
    }
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
                  if (f.isSuccess()) {
                    LOG.info("CONNECTED '{}'", name);
                    Channel ch = f.channel();
                    ch.attr(GoogolplexHandler.DEVICE_INFO_KEY).set(deviceInfo);
                    ch.closeFuture()
                        .addListener(
                            (ChannelFuture closeFuture) -> {
                              LOG.info("DISCONNECTED '{}'", name);
                              eventLoop.execute(
                                  () -> {
                                    nameToChannel.remove(name);
                                    open(name);
                                  });
                            });
                  } else {
                    Throwable cause = f.cause();
                    LOG.error(
                        "CONNECTION_FAILURE '{}' {}: {}",
                        name,
                        cause.getClass().getSimpleName(),
                        cause.getMessage());
                    eventLoop.schedule(
                        () -> {
                          nameToChannel.remove(name);
                          open(name);
                        },
                        CONNECTION_RETRY_SECONDS,
                        TimeUnit.SECONDS);
                  }
                })
            .channel();
    nameToChannel.put(name, channel);
  }

  public void unregister(ServiceEvent event) {
    eventLoop.execute(
        () -> {
          String name = serviceNameToName.get(event.getName());
          if (name != null) {
            LOG.info("UNREGISTER '{}'", name);
            nameToAddress.remove(name);
          }
        });
  }

  @Override
  public void close() {
    eventLoopGroup.shutdownGracefully();
  }
}
