package com.jyuzawa.googolplex_theater.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.jyuzawa.googolplex_theater.config.CastConfig;
import com.jyuzawa.googolplex_theater.config.CastConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.protobuf.CastMessages.CastMessage;
import com.jyuzawa.googolplex_theater.util.JsonUtil;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.vertx.core.json.JsonObject;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GoogolplexControllerTest {
	private static final Logger LOG = LoggerFactory.getLogger(GoogolplexControllerTest.class);

	static GoogolplexController controller;
	static FakeCast cast1;
	static FakeCast cast2;
	static FakeCast cast3;
	static FakeCast cast4;
	static EventLoopGroup workerGroup;

	@BeforeAll
	static void setUpBeforeClass() throws Exception {
		workerGroup = new NioEventLoopGroup(1);
		controller = new GoogolplexControllerImpl(workerGroup, GoogolplexClientHandler.DEFAULT_APPLICATION_ID, 0, 0);
		cast1 = new FakeCast(workerGroup, 9001);
		cast2 = new FakeCast(workerGroup, 9002);
		cast3 = new FakeCast(workerGroup, 9003);
		cast4 = new FakeCast(workerGroup, 9004);
	}

	@AfterAll
	static void tearDownAfterClass() throws Exception {
		CastConfig newConfig = new CastConfig(Collections.emptyList());
		controller.accept(newConfig);
		cast1.close();
		cast2.close();
		cast3.close();
		cast4.close();
		workerGroup.shutdownGracefully().syncUninterruptibly();
	}

	@Test
	void test() throws Exception {
		List<DeviceInfo> devices = new ArrayList<>();
		devices.add(cast1.device());
		devices.add(cast2.device());
		devices.add(cast3.device());
		devices.add(cast4.device());
		CastConfig config = new CastConfig(devices);
		controller.register(cast1.event());
		controller.register(cast2.event());
		controller.accept(config);
		controller.register(cast3.event());
		controller.register(cast4.event());
		controller.register(FakeCast.event(9005, "UnknownCast"));
		ServiceEvent noName = Mockito.mock(ServiceEvent.class);
		ServiceInfo noNameInfo = Mockito.mock(ServiceInfo.class);
		Mockito.when(noName.getInfo()).thenReturn(noNameInfo);
		Mockito.when(noNameInfo.getPropertyString(Mockito.anyString())).thenReturn(null);
		controller.register(noName);
		ServiceEvent noAddr = Mockito.mock(ServiceEvent.class);
		Mockito.when(noAddr.getName()).thenReturn("Chromecast-NOIP.local");
		ServiceInfo noAddrInfo = Mockito.mock(ServiceInfo.class);
		Mockito.when(noAddr.getInfo()).thenReturn(noAddrInfo);
		Mockito.when(noAddrInfo.getPropertyString(Mockito.anyString())).thenReturn("NOIP");
		Mockito.when(noAddrInfo.getInetAddresses()).thenReturn(new InetAddress[] {});
		controller.register(noAddr);

		List<JsonObject> unconfigureds = controller.getUnconfiguredDevices();
		assertEquals(1, unconfigureds.size());
		JsonObject unconfigured = unconfigureds.get(0);
		assertEquals("UnknownCast", unconfigured.getString("name"));

		List<JsonObject> configureds = controller.getConfiguredDevices();
		assertEquals(4, configureds.size());
		assertEquals(cast1.name, configureds.get(0).getString("name"));
		assertEquals(cast2.name, configureds.get(1).getString("name"));
		assertEquals(cast3.name, configureds.get(2).getString("name"));
		assertEquals(cast4.name, configureds.get(3).getString("name"));

		assertTransaction(cast1);
		assertTransaction(cast2);
		assertTransaction(cast3);
		assertTransaction(cast4);

		// idle screen on 2
		cast2.loadIdleScreen();
		assertTransaction(cast2);

		// force close on 3
		cast3.closeChannel();
		assertTransaction(cast3);

		// update 1, delete 4
		cast1.custom = "new";
		devices.set(0, cast1.device());
		devices.remove(3);
		CastConfig newConfig = new CastConfig(devices);
		controller.accept(newConfig);
		assertTransaction(cast1);

		List<JsonObject> unconfigureds2 = controller.getUnconfiguredDevices();
		assertEquals(2, unconfigureds2.size());
		List<String> unconfigureds2Names = new ArrayList<>();
		for (JsonObject device : unconfigureds2) {
			unconfigureds2Names.add(device.getString("name"));
		}
		assertTrue(unconfigureds2Names.contains(cast4.name));
		assertTrue(unconfigureds2Names.contains("UnknownCast"));

		List<JsonObject> configureds2 = controller.getConfiguredDevices();
		assertEquals(3, configureds2.size());
		List<String> configureds2Name = new ArrayList<>();
		for (JsonObject device : configureds2) {
			configureds2Name.add(device.getString("name"));
		}
		assertTrue(configureds2Name.contains(cast1.name));
		assertTrue(configureds2Name.contains(cast2.name));
		assertTrue(configureds2Name.contains(cast3.name));

		// refresh 2
		controller.refresh(cast2.name);
		assertTransaction(cast2);

		// refresh all
		controller.refresh(null);
		assertTransaction(cast1);
		assertTransaction(cast2);
		assertTransaction(cast3);

		// send broken messages which should restart 3
		cast3.sendBrokenMessages();
		assertTransaction(cast3);

		LOG.info("DONE");
	}

	private void assertTransaction(FakeCast cast) throws Exception {
		CastMessage connect = cast.getMessage();
		assertType(connect, GoogolplexClientHandler.DEFAULT_RECEIVER_ID, GoogolplexClientHandler.NAMESPACE_CONNECTION);
		assertEquals("{\"type\":\"CONNECT\"}", connect.getPayloadUtf8());

		CastMessage launch = cast.getMessage();
		assertType(launch, GoogolplexClientHandler.DEFAULT_RECEIVER_ID, GoogolplexClientHandler.NAMESPACE_RECEIVER);
		assertEquals("{\"requestId\":0,\"appId\":\"" + GoogolplexClientHandler.DEFAULT_APPLICATION_ID
				+ "\",\"type\":\"LAUNCH\"}", launch.getPayloadUtf8());

		CastMessage appConnect = cast.getMessage();
		assertType(appConnect, cast.toString(), GoogolplexClientHandler.NAMESPACE_CONNECTION);
		assertEquals("{\"type\":\"CONNECT\"}", appConnect.getPayloadUtf8());

		CastMessage app = cast.getMessage();
		assertType(app, cast.toString(), GoogolplexClientHandler.NAMESPACE_CUSTOM);
		JsonNode node = JsonUtil.MAPPER.readTree(app.getPayloadUtf8());
		assertEquals(cast.name, node.get("name").asText());
		assertEquals(cast.custom, node.get("settings").get("foo").asText());
	}

	private void assertType(CastMessage msg, String receiverId, String namespace) {
		assertEquals(CastMessage.ProtocolVersion.CASTV2_1_0, msg.getProtocolVersion());
		assertTrue(msg.getSourceId().startsWith("sender-"));
		assertEquals(receiverId, msg.getDestinationId());
		assertEquals(namespace, msg.getNamespace());
		assertEquals(CastMessage.PayloadType.STRING, msg.getPayloadType());
	}
}
