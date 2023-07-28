/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jyuzawa.googolplex_theater.DeviceConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.protobuf.Wire.CastMessage;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.CharsetUtil;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import javax.jmdns.JmDNS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

public class StepDefinitions {
    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

    @Autowired
    Path devicesPath;

    @Autowired
    WebTestClient webTestClient;

    private static FakeCast device;
    private static EventLoopGroup workerGroup;
    private int loadCount;
    private static final ObjectNode BASE_SETTINGS =
            MapperUtil.MAPPER.getNodeFactory().objectNode().put("foo", "bar");
    private static JmDNS mdns;

    @BeforeAll
    public static void start() throws Exception {
        mdns = JmDNS.create();
        workerGroup = new NioEventLoopGroup(1);
        device = new FakeCast(workerGroup, 9001);
    }

    @AfterAll
    public static void stop() throws IOException {
        device.close();
        workerGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).syncUninterruptibly();
        mdns.close();
    }

    private void writeEmptyDevices() throws IOException {
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(
                devicesPath, CharsetUtil.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            bufferedWriter.write("settings:\n  foo: bar");
        }
    }

    private void writeDevices(DeviceConfig deviceConfig) throws IOException {
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(
                devicesPath, CharsetUtil.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
            MapperUtil.YAML_MAPPER.writeValue(bufferedWriter, deviceConfig);
        }
    }

    @Before
    public void setup() throws Exception {
        writeEmptyDevices();
    }

    @After
    public void tearDown() throws IOException {
        writeEmptyDevices();
        mdns.unregisterAllServices();
    }

    @Given("a registered device with url {string}")
    public void a_registered_device_with_url(String url) throws Exception {
        ObjectNode settings = MapperUtil.MAPPER.getNodeFactory().objectNode().put("url", url);
        DeviceInfo deviceInfo = new DeviceInfo(device.name, settings);
        writeDevices(new DeviceConfig(Collections.singletonList(deviceInfo), BASE_SETTINGS));
        mdns.registerService(device.event().getInfo());
        assertTransaction(device, url);
    }

    @When("the device url is set to {string}")
    public void the_device_url_is_set_to(String url) throws Exception {
        ObjectNode settings = MapperUtil.MAPPER.getNodeFactory().objectNode().put("url", url);
        DeviceInfo deviceInfo = new DeviceInfo(device.name, settings);
        writeDevices(new DeviceConfig(Collections.singletonList(deviceInfo), BASE_SETTINGS));
    }

    @Then("the device loaded url {string}")
    public void the_device_loaded_url(String url) throws Exception {
        assertTransaction(device, url);
    }

    @Then("the device connected {int} times")
    public void the_device_connected_times(Integer times) {
        assertEquals(times.intValue(), loadCount);
    }

    @When("the device is unregistered")
    public void the_device_is_unregistered() throws IOException {
        writeEmptyDevices();
    }

    @Then("the device is not connected")
    public void the_device_is_not_connected() {
        assertFalse(device.isConnected());
    }

    @Given("an unregistered device")
    public void an_unregistered_device() throws IOException {
        mdns.registerService(device.event().getInfo());
    }

    private void refresh(String name) throws InterruptedException {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        if (name != null) {
            formData.set("name", name);
        }
        webTestClient
                .post()
                .uri("/refresh")
                .body(BodyInserters.fromFormData(formData))
                .exchange()
                .expectStatus()
                .is2xxSuccessful();
    }

    @Then("the user interface loads properly")
    public void the_user_interface_loads_properly() {
        webTestClient.get().uri("/").exchange().expectStatus().is2xxSuccessful();
    }

    @When("the device is refreshed")
    public void the_device_is_refreshed() throws InterruptedException {
        refresh(device.name);
    }

    @When("another device is refreshed")
    public void another_device_is_refreshed() throws InterruptedException {
        refresh("AnotherDevice");
    }

    @When("the devices are all refreshed")
    public void the_devices_are_all_refreshed() throws InterruptedException {
        refresh(null);
    }

    @When("the device has home screen")
    public void the_device_has_home_screen() throws IOException {
        device.loadIdleScreen();
    }

    @When("the device has connection closed")
    public void the_device_has_connection_closed() throws InterruptedException {
        device.closeChannel();
    }

    @When("the device has lost pings")
    public void the_device_has_lost_pings() throws InterruptedException {
        device.pongable = false;
        Thread.sleep(5000L);
        // that last cast should have reconnected
        assertTrue(device.pongable);
    }

    @When("the device has broken messages")
    public void the_device_has_broken_messages() throws Exception {
        device.sendBrokenMessages();
    }

    private void assertTransaction(FakeCast cast, String url) throws Exception {
        CastMessage connect = cast.getMessage();
        assertType(connect, GoogolplexClient.DEFAULT_RECEIVER_ID, GoogolplexClient.NAMESPACE_CONNECTION);
        assertEquals("{\"type\":\"CONNECT\"}", connect.getPayloadUtf8());

        CastMessage launch = cast.getMessage();
        assertType(launch, GoogolplexClient.DEFAULT_RECEIVER_ID, GoogolplexClient.NAMESPACE_RECEIVER);
        assertEquals(
                "{\"requestId\":0,\"appId\":\"" + GoogolplexClient.DEFAULT_APPLICATION_ID + "\",\"type\":\"LAUNCH\"}",
                launch.getPayloadUtf8());

        CastMessage appConnect = cast.getMessage();
        assertType(appConnect, cast.toString(), GoogolplexClient.NAMESPACE_CONNECTION);
        assertEquals("{\"type\":\"CONNECT\"}", appConnect.getPayloadUtf8());

        CastMessage app = cast.getMessage();
        assertType(app, cast.toString(), GoogolplexClient.NAMESPACE_CUSTOM);
        JsonNode node = MapperUtil.MAPPER.readTree(app.getPayloadUtf8());
        assertEquals(cast.name, node.get("name").asText());
        assertEquals(url, node.get("settings").get("url").asText());
        loadCount++;
    }

    private void assertType(CastMessage msg, String receiverId, String namespace) {
        assertEquals(CastMessage.ProtocolVersion.CASTV2_1_0, msg.getProtocolVersion());
        assertTrue(msg.getSourceId().startsWith("sender-"));
        assertEquals(receiverId, msg.getDestinationId());
        assertEquals(namespace, msg.getNamespace());
        assertEquals(CastMessage.PayloadType.STRING, msg.getPayloadType());
    }
}
