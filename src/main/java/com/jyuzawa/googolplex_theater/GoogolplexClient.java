/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import com.jyuzawa.googolplex_theater.DeviceConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.protobuf.Wire.CastMessage;
import com.jyuzawa.googolplex_theater.protobuf.Wire.CastMessage.PayloadType;
import com.jyuzawa.googolplex_theater.protobuf.Wire.CastMessage.ProtocolVersion;
import io.netty.channel.ChannelOption;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.RetrySpec;

/**
 * This class handles messages from the device and prepares proper responses. The lifecycle is very
 * simple. Once the connection is established, the controller does not send additional messages.
 * Theoretically it could, but for simplicity of lifecycle management, we do not. If the controller
 * wants to do something after the connection is established, it will close the connection and start
 * anew. Recall that any close we trigger in this handler will cause the controller to reconnect.
 *
 * @author jyuzawa
 */
@Slf4j
@Component
public final class GoogolplexClient {
    private static final Pattern APP_ID_PATTERN = Pattern.compile("^[A-Z0-9]+$");

    /**
     * This is a published application for public use. The URL is
     * https://www.jyuzawa.com/googolplex-theater/receiver/index.html
     */
    public static final String DEFAULT_APPLICATION_ID = "B1A3B99B";

    public static final String DEFAULT_RECEIVER_ID = "receiver-0";

    /** This custom namespace is used to identify messages related to our application. */
    public static final String NAMESPACE_CUSTOM = "urn:x-cast:com.jyuzawa.googolplex-theater.device";

    public static final String NAMESPACE_CONNECTION = "urn:x-cast:com.google.cast.tp.connection";
    public static final String NAMESPACE_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat";
    public static final String NAMESPACE_RECEIVER = "urn:x-cast:com.google.cast.receiver";

    private static final Map<String, Object> CONNECT_MESSAGE = Map.of("type", "CONNECT");
    private static final Map<String, Object> PING_MESSAGE = Map.of("type", "PING");

    private final String appId;
    private final Duration heartbeatInterval;
    private final Duration heartbeatTimeout;
    private final Duration retryInterval;
    private final TcpClient bootstrap;

    @Autowired
    public GoogolplexClient(
            @Value("${googolplexTheater.appId}") String appId,
            @Value("${googolplexTheater.heartbeatInterval}") Duration heartbeatInterval,
            @Value("${googolplexTheater.heartbeatTimeout}") Duration heartbeatTimeout,
            @Value("${googolplexTheater.retryInterval}") Duration retryInterval)
            throws SSLException {
        this.appId = appId;
        if (!APP_ID_PATTERN.matcher(appId).find()) {
            throw new IllegalArgumentException("Invalid cast app-id, must be " + APP_ID_PATTERN.pattern());
        }
        this.heartbeatInterval = heartbeatInterval;
        this.heartbeatTimeout = heartbeatTimeout;
        this.retryInterval = retryInterval;

        SslContext sslContext = SslContextBuilder.forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        log.info("Using cast application id: {}", appId);
        // configure the socket client
        this.bootstrap = TcpClient.create()
                .secure(spec -> spec.sslContext(sslContext))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
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
    static CastMessage generateMessage(String namespace, String senderId, String destinationId, Object payload) {
        CastMessage.Builder out = CastMessage.newBuilder();
        out.setDestinationId(destinationId);
        out.setSourceId(senderId);
        out.setNamespace(namespace);
        out.setProtocolVersion(ProtocolVersion.CASTV2_1_0);
        out.setPayloadType(PayloadType.STRING);
        try {
            out.setPayloadUtf8(MapperUtil.MAPPER.writeValueAsString(payload));
        } catch (IOException e) {
            throw new GoogolplexClientException("EncodingException", e);
        }
        return out.build();
    }

    public Mono<Void> connect(InetSocketAddress address, DeviceInfo deviceInfo) {
        return bootstrap
                .remoteAddress(() -> address)
                .connect()
                .flatMap(conn -> new MyConnection(conn, deviceInfo).handle())
                .retryWhen(RetrySpec.backoff(Long.MAX_VALUE, retryInterval).doBeforeRetry(err -> {
                    log.warn("ERROR " + deviceInfo.getName(), err.failure());
                }));
    }

    private final class MyConnection {
        private final Connection conn;
        private final DeviceInfo deviceInfo;
        private final String name;
        private final String senderId;
        private final AtomicInteger requestId;
        private final AtomicReference<Instant> lastHeartbeat;
        private final AtomicReference<String> sessionReceiverId;

        private MyConnection(Connection conn, DeviceInfo deviceInfo) {
            this.conn = conn;
            this.deviceInfo = deviceInfo;
            this.name = deviceInfo.getName();
            this.senderId = "sender-" + ThreadLocalRandom.current().nextInt();
            this.requestId = new AtomicInteger();
            this.lastHeartbeat = new AtomicReference<>(Instant.now());
            this.sessionReceiverId = new AtomicReference<>();
        }

        private Mono<Void> handle() {
            log.info("CONNECT '{}'", name);
            conn.addHandlerLast("frameDecoder", new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
            conn.addHandlerLast("protobufDecoder", new ProtobufDecoder(CastMessage.getDefaultInstance()));
            conn.addHandlerLast("frameEncoder", new LengthFieldPrepender(4));
            conn.addHandlerLast("protobufEncoder", new ProtobufEncoder());
            return start().then(conn.inbound()
                            .receiveObject()
                            .cast(CastMessage.class)
                            .flatMap(this::handle)
                            .then())
                    .doFinally(sig -> {
                        log.info("DISCONNECT '{}'", name);
                        conn.dispose();
                    })
                    .switchIfEmpty(Mono.error(new GoogolplexClientException("connection closed by peer")));
        }

        private Mono<Void> start() {
            // initial connect
            CastMessage initialConnectMessage =
                    generateMessage(NAMESPACE_CONNECTION, senderId, DEFAULT_RECEIVER_ID, CONNECT_MESSAGE);

            // launch
            Map<String, Object> launch = new HashMap<>();
            launch.put("type", "LAUNCH");
            launch.put("appId", appId);
            launch.put("requestId", requestId.getAndIncrement());
            CastMessage launchMessage = generateMessage(NAMESPACE_RECEIVER, senderId, DEFAULT_RECEIVER_ID, launch);

            return conn.outbound()
                    .sendObject(initialConnectMessage)
                    .then(conn.outbound().sendObject(launchMessage))
                    .then();
        }

        protected Mono<Void> handle(CastMessage msg) {
            // do some rudimentary validation
            if (msg.getProtocolVersion() != ProtocolVersion.CASTV2_1_0 || msg.getPayloadType() != PayloadType.STRING) {
                log.debug("Invalid message");
                return Mono.empty();
            }
            String sourceId = msg.getSourceId();
            if (!(sourceId.equals(DEFAULT_RECEIVER_ID) || sourceId.equals(sessionReceiverId.get()))) {
                log.debug("Invalid message source");
                return Mono.empty();
            }
            String destinationId = msg.getDestinationId();
            if (!(destinationId.equals(senderId) || destinationId.equals("*"))) {
                log.debug("Invalid message destination");
                return Mono.empty();
            }
            // handle different namespaces differently
            String namespace = msg.getNamespace();
            if (NAMESPACE_HEARTBEAT.equals(namespace)) {
                lastHeartbeat.set(Instant.now());
                return Mono.empty();
            }
            if (NAMESPACE_CUSTOM.equals(namespace)) {
                log.info("MESSAGE '{}' {}", name, msg.getPayloadUtf8());
                return Mono.empty();
            }
            if (!NAMESPACE_RECEIVER.equals(namespace)) {
                log.debug("other message");
                return Mono.empty();
            }
            ReceiverResponse receiverPayload;
            try {
                receiverPayload = MapperUtil.MAPPER.readValue(msg.getPayloadUtf8(), ReceiverResponse.class);
            } catch (IOException e) {
                return Mono.error(e);
            }
            if (receiverPayload.getReason() != null) {
                // the presence of the reason indicates the launch likely failed for some reason
                log.warn("ERROR '{}' {}", name, msg.getPayloadUtf8());
                // close to reload connection
                return Mono.error(new GoogolplexClientException("BadReceiverReason"));
            }
            if (!receiverPayload.isApplicationStatus()) {
                return Mono.empty();
            }
            if (receiverPayload.isIdleScreen()) {
                /*
                 * if the idle screen is back, the receiver app has crashed for some reason, so close which will trigger a
                 * refresh.
                 */
                log.info("DOWN '{}'", name);
                return Mono.error(new GoogolplexClientException("IdleScreen"));
            }
            String transportId = receiverPayload.getApplicationTransportId(appId);
            if (transportId == null) {
                return Mono.empty();
            }
            /*
             * if transportId is present for our appId, then we can send the settings thru our custom namespace
             */
            if (sessionReceiverId.getAndSet(transportId) != null) {
                return Mono.empty();
            }
            log.info("UP '{}'", name);
            // session connect
            CastMessage sessionConnectMessage =
                    generateMessage(NAMESPACE_CONNECTION, senderId, transportId, CONNECT_MESSAGE);
            // display data custom message
            Map<String, Object> custom = new HashMap<>();
            custom.put("name", name);
            custom.put("settings", deviceInfo.getSettings());
            custom.put("requestId", requestId.getAndIncrement());
            CastMessage customMessage = generateMessage(NAMESPACE_CUSTOM, senderId, transportId, custom);
            CastMessage heartbeatMessage =
                    generateMessage(NAMESPACE_HEARTBEAT, senderId, DEFAULT_RECEIVER_ID, PING_MESSAGE);
            return conn.outbound()
                    .sendObject(sessionConnectMessage)
                    .then(conn.outbound().sendObject(customMessage))
                    .then()
                    .then(Flux.interval(heartbeatInterval)
                            .flatMap(i -> {
                                if (lastHeartbeat.get().plus(heartbeatTimeout).isBefore(Instant.now())) {
                                    /* the last heartbeat occurred too long ago, so close to trigger a reconnect */
                                    log.warn("EXPIRE '{}'", name);
                                    return Mono.error(new GoogolplexClientException("HeartbeatTimeout"));
                                } else {
                                    // send another heartbeat
                                    return conn.outbound().sendObject(heartbeatMessage);
                                }
                            })
                            .then());
        }
    }
}
