/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater.client;

import com.jyuzawa.googolplex_theater.config.DeviceConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.protobuf.Wire.CastMessage;
import com.jyuzawa.googolplex_theater.protobuf.Wire.CastMessage.PayloadType;
import com.jyuzawa.googolplex_theater.protobuf.Wire.CastMessage.ProtocolVersion;
import com.jyuzawa.googolplex_theater.util.MapperUtil;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;

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
public final class GoogolplexClientHandler implements Function<Connection, Mono<Void>> {
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

    public static final String DEFAULT_RECEIVER_ID = "receiver-0";

    /** This custom namespace is used to identify messages related to our application. */
    public static final String NAMESPACE_CUSTOM = "urn:x-cast:com.jyuzawa.googolplex-theater.device";

    public static final String NAMESPACE_CONNECTION = "urn:x-cast:com.google.cast.tp.connection";
    public static final String NAMESPACE_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat";
    public static final String NAMESPACE_RECEIVER = "urn:x-cast:com.google.cast.receiver";

    private static final Map<String, Object> CONNECT_MESSAGE = messagePayload("CONNECT");
    private static final Map<String, Object> PING_MESSAGE = messagePayload("PING");

    private final String appId;
    private String senderId;
    private String sessionReceiverId;
    private int requestId;

    private Instant lastHeartbeat;
    private final int heartbeatIntervalSeconds;
    private final Duration heartbeatTimeout;
    private final DeviceInfo deviceInfo;

    public GoogolplexClientHandler(
            String appId, int heartbeatIntervalSeconds, int heartbeatTimeoutSeconds, DeviceInfo deviceInfo) {
        this.deviceInfo = deviceInfo;
        this.appId = appId;
        this.senderId = "sender-" + System.identityHashCode(this);
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.heartbeatTimeout = Duration.ofSeconds(heartbeatTimeoutSeconds);
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
    private CastMessage generateMessage(String namespace, String destinationId, Map<String, Object> payload) {
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
            e.printStackTrace();
        }
        return out.build();
    }

    /**
     * The connection is up. Configures keep alive messages and timeout. Sends an initial connect
     * message and launch message. The device will respond back with a receiver status message (or
     * error if the receiver could not be started). That response and the keep alive responses from
     * the device are all handled in the channelRead0().
     */
    public Mono<Void> channelActive(Connection ctx) {
        // initial connect
        CastMessage initialConnectMessage = generateMessage(NAMESPACE_CONNECTION, DEFAULT_RECEIVER_ID, CONNECT_MESSAGE);

        // launch
        Map<String, Object> launch = new HashMap<>();
        launch.put("type", "LAUNCH");
        launch.put("appId", appId);
        launch.put("requestId", requestId++);
        CastMessage launchMessage = generateMessage(NAMESPACE_RECEIVER, DEFAULT_RECEIVER_ID, launch);

        /*
         * there was no heartbeat now, but we initialize with the start time, so we don't timeout immediately.
         */
        this.lastHeartbeat = Instant.now();

        return ctx.outbound()
                .sendObject(initialConnectMessage)
                .then(ctx.outbound().sendObject(launchMessage))
                .then();
    }

    protected Mono<Void> channelRead0(Connection ctx, CastMessage msg) throws IOException {
        // do some rudimentary validation
        if (msg.getProtocolVersion() != ProtocolVersion.CASTV2_1_0 || msg.getPayloadType() != PayloadType.STRING) {
            log.debug("Invalid message");
            return Mono.empty();
        }
        String sourceId = msg.getSourceId();
        if (!(sourceId.equals(DEFAULT_RECEIVER_ID) || sourceId.equals(sessionReceiverId))) {
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
        String name = deviceInfo.getName();
        switch (namespace) {
            case NAMESPACE_HEARTBEAT:
                lastHeartbeat = Instant.now();
                break;
            case NAMESPACE_RECEIVER:
                return handleReceiverMessage(ctx, msg);
            case NAMESPACE_CUSTOM:
                log.info("MESSAGE '{}' {}", name, msg.getPayloadUtf8());
                break;
            default:
                log.debug("other message");
                break;
        }
        return Mono.empty();
    }

    /**
     * Determine how to respond or act upon receiving a response from the receiver.
     *
     * @param ctx channel context
     * @param msg a receiver message
     * @param device the device name and settings
     * @throws IOException when the JSON serialization fails
     */
    private Mono<Void> handleReceiverMessage(Connection ctx, CastMessage msg) throws IOException {
        ReceiverResponse receiverPayload = MapperUtil.MAPPER.readValue(msg.getPayloadUtf8(), ReceiverResponse.class);
        String name = deviceInfo.getName();
        if (receiverPayload.getReason() != null) {
            // the presence of the reason indicates the launch likely failed for some reason
            log.warn("ERROR '{}' {}", name, msg.getPayloadUtf8());
            // close to reload connection
            return Mono.error(new RuntimeException("badPayload"));
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
            return Mono.error(new RuntimeException("IdleScreen"));
        }
        String transportId = receiverPayload.getApplicationTransportId(appId);
        /*
         * if transportId is present for our appId, then we can send the settings thru our custom namespace
         */
        if (sessionReceiverId == null && transportId != null) {
            sessionReceiverId = transportId;
            log.info("UP '{}'", name);
            // session connect
            CastMessage sessionConnectMessage = generateMessage(NAMESPACE_CONNECTION, transportId, CONNECT_MESSAGE);
            // display data custom message
            Map<String, Object> custom = new HashMap<>();
            custom.put("name", deviceInfo.getName());
            custom.put("settings", deviceInfo.getSettings());
            custom.put("requestId", requestId++);
            CastMessage customMessage = generateMessage(NAMESPACE_CUSTOM, transportId, custom);
            return ctx.outbound()
                    .sendObject(sessionConnectMessage)
                    .then(ctx.outbound().sendObject(customMessage))
                    .then()
                    .then(Flux.interval(Duration.ofSeconds(heartbeatIntervalSeconds))
                            .flatMap(i -> {
                                if (lastHeartbeat.plus(heartbeatTimeout).isBefore(Instant.now())) {
                                    /* the last heartbeat occurred too long ago, so close to trigger a reconnect */
                                    log.warn("EXPIRE '{}'", deviceInfo.getName());
                                    return Mono.error(new RuntimeException("Expires"));
                                } else {
                                    // send another heartbeat
                                    CastMessage heartbeatMessage =
                                            generateMessage(NAMESPACE_HEARTBEAT, DEFAULT_RECEIVER_ID, PING_MESSAGE);
                                    return ctx.outbound().sendObject(heartbeatMessage);
                                }
                            })
                            .then());
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> apply(Connection conn) {
        conn.addHandlerLast("frameDecoder", new LengthFieldBasedFrameDecoder(1048576, 0, 4, 0, 4));
        conn.addHandlerLast("protobufDecoder", new ProtobufDecoder(CastMessage.getDefaultInstance()));
        conn.addHandlerLast("frameEncoder", new LengthFieldPrepender(4));
        conn.addHandlerLast("protobufEncoder", new ProtobufEncoder());
        conn.addHandlerLast("logger", new LoggingHandler());
        senderId = "sender-" + ThreadLocalRandom.current().nextInt();
        sessionReceiverId = null;
        requestId = 0;
        return channelActive(conn)
                .then(conn.inbound()
                        .receiveObject()
                        .cast(CastMessage.class)
                        .flatMap(msg -> {
                            try {
                                return channelRead0(conn, msg);
                            } catch (IOException e) {
                                return Mono.error(e);
                            }
                        })
                        .then())
                .doFinally(sig -> conn.dispose())
                .flatMap(v -> Mono.error(new RuntimeException("connection closed by peer")));
    }
}
