/*
 * Copyright (c) 2022 James Yuzawa (https://www.jyuzawa.com/)
 * SPDX-License-Identifier: MIT
 */
package com.jyuzawa.googolplex_theater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jyuzawa.googolplex_theater.DeviceConfig.DeviceInfo;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

class GoogolplexServiceTest {

    static GoogolplexClient client;
    static GoogolplexService service;
    static FakeCast cast1;
    static FakeCast cast2;
    static FakeCast cast3;
    static FakeCast cast4;
    static EventLoopGroup workerGroup;

    @BeforeAll
    static void setUpBeforeClass() throws Exception {
        workerGroup = new NioEventLoopGroup(1);
        client = Mockito.mock(GoogolplexClient.class);
        Mockito.when(client.connect(Mockito.any(), Mockito.any())).thenReturn(Mono.empty());
        service = new GoogolplexService(client);
        cast1 = new FakeCast(workerGroup, 9001);
        cast2 = new FakeCast(workerGroup, 9002);
        cast3 = new FakeCast(workerGroup, 9003);
        cast4 = new FakeCast(workerGroup, 9004);
    }

    @AfterAll
    static void tearDownAfterClass() throws Exception {
        service.close();
        cast1.close();
        cast2.close();
        cast3.close();
        cast4.close();
        workerGroup.shutdownGracefully(100, 100, TimeUnit.MILLISECONDS).syncUninterruptibly();
    }

    @Test
    void test() throws Exception {
        List<DeviceInfo> devices = new ArrayList<>();
        devices.add(cast1.device());
        devices.add(cast2.device());
        devices.add(cast3.device());
        devices.add(cast4.device());
        DeviceConfig config = new DeviceConfig(devices, null);
        service.register(cast1.event()).get();
        service.register(cast2.event()).get();
        Mockito.verify(client, Mockito.never()).connect(Mockito.any(), Mockito.any());
        service.processDeviceConfig(config).get();
        Mockito.verify(client).connect(Mockito.any(), Mockito.eq(cast1.device()));
        Mockito.verify(client).connect(Mockito.any(), Mockito.eq(cast2.device()));
        Mockito.verify(client, Mockito.never()).connect(Mockito.any(), Mockito.eq(cast3.device()));
        Mockito.verify(client, Mockito.never()).connect(Mockito.any(), Mockito.eq(cast4.device()));
        service.register(cast3.event()).get();
        service.register(cast4.event()).get();
        Mockito.verify(client).connect(Mockito.any(), Mockito.eq(cast3.device()));
        Mockito.verify(client).connect(Mockito.any(), Mockito.eq(cast4.device()));
        service.register(FakeCast.event(9005, "UnknownCast")).get();
        ServiceEvent noName = Mockito.mock(ServiceEvent.class);
        ServiceInfo noNameInfo = Mockito.mock(ServiceInfo.class);
        Mockito.when(noName.getInfo()).thenReturn(noNameInfo);
        Mockito.when(noNameInfo.getPropertyString(Mockito.anyString())).thenReturn(null);
        service.register(noName).get();
        ServiceEvent noAddr = Mockito.mock(ServiceEvent.class);
        Mockito.when(noAddr.getName()).thenReturn("Chromecast-NOIP.local");
        ServiceInfo noAddrInfo = Mockito.mock(ServiceInfo.class);
        Mockito.when(noAddr.getInfo()).thenReturn(noAddrInfo);
        Mockito.when(noAddrInfo.getPropertyString(Mockito.anyString())).thenReturn("NOIP");
        Mockito.when(noAddrInfo.getInetAddresses()).thenReturn(new InetAddress[] {});
        service.register(noAddr).get();

        List<DeviceStatus> deviceInfos = service.getDeviceInfo();
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

    private Set<String> getUnconfigureds(List<DeviceStatus> devices) {
        Set<String> out = new HashSet<>();
        for (DeviceStatus device : devices) {
            if (null == device.getSettings()) {
                out.add(device.getName());
            }
        }
        return out;
    }

    private Set<String> getConfigureds(List<DeviceStatus> devices) {
        Set<String> out = new HashSet<>();
        for (DeviceStatus device : devices) {
            if (null != device.getSettings()) {
                out.add(device.getName());
            }
        }
        return out;
    }
}
