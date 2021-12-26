package com.jyuzawa.googolplex_theater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.google.common.jimfs.WatchServiceConfiguration;
import com.jyuzawa.googolplex_theater.client.FakeCast;
import com.jyuzawa.googolplex_theater.client.GoogolplexClientHandler;
import com.jyuzawa.googolplex_theater.config.DeviceConfig;
import com.jyuzawa.googolplex_theater.config.DeviceConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.config.GoogolplexTheaterConfig;
import com.jyuzawa.googolplex_theater.config.GoogolplexTheaterConfig.ConfigYaml;
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
import io.netty.util.CharsetUtil;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.junit5.VertxTestContext;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class StepDefinitions {

  private static Vertx vertx;
  private static FakeCast device;
  private static EventLoopGroup workerGroup;
  private int loadCount;
  private GoogolplexTheater googolplexTheater;
  private static Path confPath;
  private static Path devicesPath;
  private static final ObjectNode BASE_SETTINGS =
      MapperUtil.MAPPER.getNodeFactory().objectNode().put("foo", "bar");

  @BeforeAll
  public static void start() throws Exception {
    vertx = Vertx.vertx();
    workerGroup = vertx.nettyEventLoopGroup();
    device = new FakeCast(workerGroup, 9001);
    // For a simple file system with Unix-style paths and behavior:
    FileSystem fs =
        Jimfs.newFileSystem(
            Configuration.unix().toBuilder()
                .setWatchServiceConfiguration(
                    WatchServiceConfiguration.polling(10, TimeUnit.MILLISECONDS))
                .build());
    confPath = fs.getPath("/conf");
    Files.createDirectory(confPath);
    devicesPath = confPath.resolve("devices.yml");
    try (BufferedWriter bufferedWriter =
        Files.newBufferedWriter(
            confPath.resolve("config.yml"),
            CharsetUtil.UTF_8,
            StandardOpenOption.WRITE,
            StandardOpenOption.CREATE)) {
      ConfigYaml config = new ConfigYaml();
      config.baseReconnectSeconds = 0;
      config.reconnectNoiseSeconds = 0;
      config.heartbeatIntervalSeconds = 1;
      config.heartbeatTimeoutSeconds = 3;
      config.discoveryNetworkInterface = "127.0.0.1";
      System.out.println(MapperUtil.YAML_MAPPER.writeValueAsString(config));
      MapperUtil.YAML_MAPPER.writeValue(bufferedWriter, config);
    }
  }

  @AfterAll
  public static void stop() throws IOException {
    device.close();
    vertx.close();
    workerGroup.shutdownGracefully().syncUninterruptibly();
  }

  private static void writeEmptyDevices() throws IOException {
    try (BufferedWriter bufferedWriter =
        Files.newBufferedWriter(
            devicesPath, CharsetUtil.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
      bufferedWriter.write("settings:\n  foo: bar");
    }
  }

  private static void writeDevices(DeviceConfig deviceConfig) throws IOException {
    try (BufferedWriter bufferedWriter =
        Files.newBufferedWriter(
            devicesPath, CharsetUtil.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
      MapperUtil.YAML_MAPPER.writeValue(bufferedWriter, deviceConfig);
    }
  }

  @Before
  public void setup() throws Exception {
    writeEmptyDevices();
    googolplexTheater = new GoogolplexTheater(GoogolplexTheaterConfig.load(confPath));
  }

  @After
  public void tearDown() throws IOException {
    googolplexTheater.close();
  }

  @Given("a registered device with url {string}")
  public void a_registered_device_with_url(String url) throws Exception {
    ObjectNode settings = MapperUtil.MAPPER.getNodeFactory().objectNode().put("url", url);
    DeviceInfo deviceInfo = new DeviceInfo(device.name, settings);
    writeDevices(new DeviceConfig(Collections.singletonList(deviceInfo), BASE_SETTINGS));
    googolplexTheater.getJmDNS().registerService(device.event().getInfo());
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
    googolplexTheater.getJmDNS().registerService(device.event().getInfo());
  }

  private void refresh(String name) throws InterruptedException {
    VertxTestContext testContext = new VertxTestContext();
    WebClient client = WebClient.create(vertx);
    MultiMap form = MultiMap.caseInsensitiveMultiMap();
    final String displayName;
    if (name == null) {
      displayName = "All Devices";
    } else {
      displayName = name;
      form.add("name", name);
    }
    client
        .post(8000, "localhost", "/refresh")
        .as(BodyCodec.string())
        .sendForm(
            form,
            testContext.succeeding(
                response ->
                    testContext.verify(
                        () -> {
                          assertEquals(200, response.statusCode());
                          assertTrue(response.body().contains(displayName + " refreshing..."));
                          testContext.completeNow();
                        })));
    testContext.awaitCompletion(10, TimeUnit.SECONDS);
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
