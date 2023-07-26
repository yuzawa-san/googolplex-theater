/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jyuzawa.googolplex_theater.DeviceConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.protobuf.Wire.CastMessage;
import com.jyuzawa.googolplex_theater.protobuf.Wire.CastMessage.PayloadType;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.impl.ServiceInfoImpl;
import org.mockito.Mockito;

public class FakeCast implements Closeable {
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private final Channel serverChannel;
    private volatile Channel channel;
    private final NioEventLoopGroup bossGroup;
    private final BlockingQueue<CastMessage> queue;
    private final int port;
    public final String name;
    String custom;
    public boolean pongable;

    public FakeCast(EventLoopGroup workerGroup, int port) throws Exception {
        this.port = port;
        this.name = "FakeCastOnPort" + port;
        this.queue = new LinkedBlockingDeque<>();
        this.bossGroup = new NioEventLoopGroup(1);
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        SelfSignedCertificate ssc = new SelfSignedCertificate();
        SslContext sslContext =
                SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        serverBootstrap
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast("ssl", sslContext.newHandler(ch.alloc()));
                        p.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
                        p.addLast("protobufDecoder", new ProtobufDecoder(CastMessage.getDefaultInstance()));
                        p.addLast("frameEncoder", new LengthFieldPrepender(4));
                        p.addLast("protobufEncoder", new ProtobufEncoder());
                        p.addLast("logger", new LoggingHandler());
                        p.addLast("handler", new FakeChromecastHandler());
                    }
                });
        this.serverChannel = serverBootstrap.bind(port).syncUninterruptibly().channel();
        this.custom = String.valueOf(ThreadLocalRandom.current().nextInt());
    }

    public DeviceInfo device() throws JsonMappingException, JsonProcessingException {
        return new DeviceInfo(name, (ObjectNode) MapperUtil.MAPPER.readTree("{\"foo\":\"" + custom + "\"}"));
    }

    public ServiceEvent event() throws UnknownHostException {
        return event(port, name);
    }

    public static ServiceEvent event(int port, String name) throws UnknownHostException {
        ServiceEvent event = Mockito.mock(ServiceEvent.class);
        Mockito.when(event.getName()).thenReturn("Chromecast-FAKE-" + port + ".local");
        ServiceInfoImpl serviceInfo = Mockito.spy((ServiceInfoImpl) ServiceInfo.create(
                ServiceDiscovery.MDNS_SERVICE_NAME, "Chromecast-FAKE-" + port + ".local", port, "fn=" + name));
        Mockito.when(event.getInfo()).thenReturn(serviceInfo);
        Mockito.when(serviceInfo.getPropertyString(Mockito.anyString())).thenReturn(name);
        Mockito.when(serviceInfo.getInetAddresses()).thenReturn(new InetAddress[] {LOOPBACK});
        Mockito.when(serviceInfo.getPort()).thenReturn(port);
        return event;
    }

    @Override
    public void close() throws IOException {
        serverChannel.close().syncUninterruptibly();
        bossGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    public CastMessage getMessage() throws InterruptedException {
        CastMessage message = queue.poll(10, TimeUnit.SECONDS);
        if (message == null) {
            fail("fake cast did not get expected message");
        }
        return message;
    }

    public void closeChannel() throws InterruptedException {
        channel.close().sync();
    }

    public void sendBrokenMessages() throws Exception {
        // send a dummy heartbeat
        CastMessage heartbeatMessage = GoogolplexClient.generateMessage(
                GoogolplexClient.NAMESPACE_HEARTBEAT,
                GoogolplexClient.DEFAULT_RECEIVER_ID,
                "*",
                Map.of("type", "PONG"));
        channel.writeAndFlush(heartbeatMessage);
        // send a bad heartbeat
        CastMessage badHeartbeatMessage = GoogolplexClient.generateMessage(
                GoogolplexClient.NAMESPACE_HEARTBEAT,
                GoogolplexClient.DEFAULT_RECEIVER_ID,
                "BAD!",
                Map.of("type", "PONG"));
        channel.writeAndFlush(badHeartbeatMessage);
        // send a bad heartbeat
        CastMessage badHeartbeatMessage2 = GoogolplexClient.generateMessage(
                GoogolplexClient.NAMESPACE_HEARTBEAT, "BAD!!!", "BAD!", Map.of("type", "PONG"));
        channel.writeAndFlush(badHeartbeatMessage2);
        // send a random namespace
        CastMessage random = GoogolplexClient.generateMessage(
                "random", GoogolplexClient.DEFAULT_RECEIVER_ID, "*", Map.of("type", "PONG"));
        channel.writeAndFlush(random);
        // send a custom namespace
        CastMessage custom = GoogolplexClient.generateMessage(
                GoogolplexClient.NAMESPACE_CUSTOM, GoogolplexClient.DEFAULT_RECEIVER_ID, "*", Map.of("type", "custom"));
        channel.writeAndFlush(custom);
        // bad
        channel.writeAndFlush(CastMessage.newBuilder().setPayloadType(PayloadType.BINARY));
        // error
        {
            ReceiverResponse.Status status = new ReceiverResponse.Status(Collections.emptyList());
            ReceiverResponse response =
                    new ReceiverResponse(1, ReceiverResponse.TYPE_RECEIVER_STATUS, status, "BROKEN");
            CastMessage launchedMessage = GoogolplexClient.generateMessage(
                    GoogolplexClient.NAMESPACE_RECEIVER, GoogolplexClient.DEFAULT_RECEIVER_ID, "*", response);
            channel.writeAndFlush(launchedMessage);
        }
        // empty application update
        {
            ReceiverResponse.Status status = new ReceiverResponse.Status(Collections.emptyList());
            ReceiverResponse response = new ReceiverResponse(1, ReceiverResponse.TYPE_RECEIVER_STATUS, status, null);
            CastMessage launchedMessage = GoogolplexClient.generateMessage(
                    GoogolplexClient.NAMESPACE_RECEIVER, GoogolplexClient.DEFAULT_RECEIVER_ID, "*", response);
            channel.writeAndFlush(launchedMessage);
        }
    }

    public void loadIdleScreen() throws IOException {
        ReceiverResponse.Application application =
                new ReceiverResponse.Application("HOME", true, FakeCast.class.toString());
        ReceiverResponse.Status status = new ReceiverResponse.Status(Collections.singletonList(application));
        ReceiverResponse response = new ReceiverResponse(1, ReceiverResponse.TYPE_RECEIVER_STATUS, status, null);
        CastMessage idleMessage = GoogolplexClient.generateMessage(
                GoogolplexClient.NAMESPACE_RECEIVER, GoogolplexClient.DEFAULT_RECEIVER_ID, "*", response);
        channel.writeAndFlush(idleMessage);
    }

    private class FakeChromecastHandler extends SimpleChannelInboundHandler<CastMessage> {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            pongable = true;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, CastMessage msg) throws Exception {
            channel = ctx.channel();

            switch (msg.getNamespace()) {
                case GoogolplexClient.NAMESPACE_HEARTBEAT:
                    if (pongable) {
                        CastMessage heartbeatMessage = GoogolplexClient.generateMessage(
                                GoogolplexClient.NAMESPACE_HEARTBEAT,
                                GoogolplexClient.DEFAULT_RECEIVER_ID,
                                msg.getSourceId(),
                                Map.of("type", "PONG"));
                        channel.writeAndFlush(heartbeatMessage);
                    }
                    break;
                case GoogolplexClient.NAMESPACE_RECEIVER:
                    ReceiverResponse.Application application = new ReceiverResponse.Application(
                            GoogolplexClient.DEFAULT_APPLICATION_ID, false, FakeCast.this.toString());
                    ReceiverResponse.Status status =
                            new ReceiverResponse.Status(Collections.singletonList(application));
                    ReceiverResponse response =
                            new ReceiverResponse(1, ReceiverResponse.TYPE_RECEIVER_STATUS, status, null);
                    CastMessage launchedMessage = GoogolplexClient.generateMessage(
                            GoogolplexClient.NAMESPACE_RECEIVER,
                            GoogolplexClient.DEFAULT_RECEIVER_ID,
                            msg.getSourceId(),
                            response);
                    channel.writeAndFlush(launchedMessage);
                case GoogolplexClient.NAMESPACE_CUSTOM:
                default:
                    queue.add(msg);
                    break;
            }
        }
    }

    public boolean isConnected() {
        channel.closeFuture().awaitUninterruptibly(10, TimeUnit.SECONDS);
        return channel.isActive();
    }
}
