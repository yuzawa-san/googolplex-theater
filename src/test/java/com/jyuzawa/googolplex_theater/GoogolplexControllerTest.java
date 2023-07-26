/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jyuzawa.googolplex_theater.DeviceConfig;
import com.jyuzawa.googolplex_theater.DeviceConfig.DeviceInfo;
import com.jyuzawa.googolplex_theater.GoogolplexService;
import com.jyuzawa.googolplex_theater.config.GoogolplexTheaterConfig;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.vertx.core.json.JsonObject;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

    static GoogolplexService controller;
    static FakeCast cast1;
    static FakeCast cast2;
    static FakeCast cast3;
    static FakeCast cast4;
    static EventLoopGroup workerGroup;

    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        workerGroup = new NioEventLoopGroup(1);
        controller = new GoogolplexService(workerGroup, GoogolplexTheaterConfig.load());
        cast1 = new FakeCast(workerGroup, 9001);
        cast2 = new FakeCast(workerGroup, 9002);
        cast3 = new FakeCast(workerGroup, 9003);
        cast4 = new FakeCast(workerGroup, 9004);
    }

    @AfterAll
    static void tearDownAfterClass() throws Exception {
        DeviceConfig newConfig = new DeviceConfig();
        controller.processDeviceConfig(newConfig);
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
        DeviceConfig config = new DeviceConfig(devices, null);
        controller.register(cast1.event());
        controller.register(cast2.event());
        controller.processDeviceConfig(config);
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

        List<JsonObject> deviceInfos = controller.getDeviceInfo();
        Set<String> configureds = getConfigureds(deviceInfos);
        assertEquals(4, configureds.size());
        assertTrue(configureds.contains(cast1.name));
        assertTrue(configureds.contains(cast2.name));
        assertTrue(configureds.contains(cast3.name));
        assertTrue(configureds.contains(cast4.name));
        Set<String> unconfigureds = getUnconfigureds(deviceInfos);
        assertEquals(1, unconfigureds.size());
        assertTrue(unconfigureds.contains("UnknownCast"));
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

    @Test
    public void durationTest() {
        assertEquals("1s", GoogolplexService.calculateDuration(Duration.ofSeconds(1)));
        assertEquals("1m0s", GoogolplexService.calculateDuration(Duration.ofMinutes(1)));
        assertEquals("1h0m0s", GoogolplexService.calculateDuration(Duration.ofHours(1)));
        assertEquals("1d0h0m0s", GoogolplexService.calculateDuration(Duration.ofDays(1)));
        assertEquals("1d1h1m1s", GoogolplexService.calculateDuration(Duration.ofSeconds(90061)));
    }
}
