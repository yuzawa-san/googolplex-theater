package com.jyuzawa.googolplex_theater.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jyuzawa.googolplex_theater.config.DeviceConfig;
import com.jyuzawa.googolplex_theater.config.DeviceConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.protobuf.Wire.CastMessage;
import com.jyuzawa.googolplex_theater.util.MapperUtil;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StepDefinitions {

  private static FakeCast device;
  private GoogolplexController controller;
  private static EventLoopGroup workerGroup;
  private int loadCount;

  @BeforeAll
  public static void start() throws Exception {
    workerGroup = new NioEventLoopGroup(1);
    device = new FakeCast(workerGroup, 9001);
  }

  @AfterAll
  public static void stop() throws IOException {
    device.close();
    workerGroup.shutdownGracefully().syncUninterruptibly();
  }

  @Before
  public void setup() throws Exception {
    controller =
        new GoogolplexControllerImpl(
            workerGroup, GoogolplexClientHandler.DEFAULT_APPLICATION_ID, 0, 0, 1, 3);
  }

  @After
  public void tearDown() throws IOException {
    DeviceConfig newConfig = new DeviceConfig();
    controller.processDeviceConfig(newConfig);
  }

  @Given("a registered device with url {string}")
  public void a_registered_device_with_url(String url) throws Exception {
    ObjectNode settings = MapperUtil.MAPPER.getNodeFactory().objectNode().put("url", url);
    DeviceInfo deviceInfo = new DeviceInfo(device.name, settings);
    controller.processDeviceConfig(new DeviceConfig(Collections.singletonList(deviceInfo), null));
    controller.register(device.event());
    assertTransaction(device, url);
  }

  @When("the device url is set to {string}")
  public void the_device_url_is_set_to(String url) throws Exception {
    ObjectNode settings = MapperUtil.MAPPER.getNodeFactory().objectNode().put("url", url);
    DeviceInfo deviceInfo = new DeviceInfo(device.name, settings);
    controller.processDeviceConfig(new DeviceConfig(Collections.singletonList(deviceInfo), null));
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
  public void the_device_is_unregistered() {
    controller.processDeviceConfig(new DeviceConfig());
  }

  @Then("the device is not connected")
  public void the_device_is_not_connected() {
    assertFalse(device.isConnected());
  }

  @Given("an unregistered device")
  public void an_unregistered_device() throws IOException {
    controller.register(device.event());
  }

  @When("the device is refreshed")
  public void the_device_is_refreshed() {
    controller.refresh(device.name);
  }

  @When("another device is refreshed")
  public void another_device_is_refreshed() {
    controller.refresh("SomeOtherDevice");
  }

  @When("the devices are all refreshed")
  public void the_devices_are_all_refreshed() {
    controller.refresh(null);
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
    assertType(
        connect,
        GoogolplexClientHandler.DEFAULT_RECEIVER_ID,
        GoogolplexClientHandler.NAMESPACE_CONNECTION);
    assertEquals("{\"type\":\"CONNECT\"}", connect.getPayloadUtf8());

    CastMessage launch = cast.getMessage();
    assertType(
        launch,
        GoogolplexClientHandler.DEFAULT_RECEIVER_ID,
        GoogolplexClientHandler.NAMESPACE_RECEIVER);
    assertEquals(
        "{\"requestId\":0,\"appId\":\""
            + GoogolplexClientHandler.DEFAULT_APPLICATION_ID
            + "\",\"type\":\"LAUNCH\"}",
        launch.getPayloadUtf8());

    CastMessage appConnect = cast.getMessage();
    assertType(appConnect, cast.toString(), GoogolplexClientHandler.NAMESPACE_CONNECTION);
    assertEquals("{\"type\":\"CONNECT\"}", appConnect.getPayloadUtf8());

    CastMessage app = cast.getMessage();
    assertType(app, cast.toString(), GoogolplexClientHandler.NAMESPACE_CUSTOM);
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

  private Set<String> getUnconfigureds(List<JsonObject> devices) {
    Set<String> out = new HashSet<>();
    for (JsonObject device : devices) {
      if (null == device.getString("settings")) {
        out.add(device.getString("name"));
      }
    }
    return out;
  }

  private Set<String> getConfigureds(List<JsonObject> devices) {
    Set<String> out = new HashSet<>();
    for (JsonObject device : devices) {
      if (null != device.getString("settings")) {
        out.add(device.getString("name"));
      }
    }
    return out;
  }
}
