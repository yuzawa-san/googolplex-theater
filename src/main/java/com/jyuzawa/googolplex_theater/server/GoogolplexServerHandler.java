package com.jyuzawa.googolplex_theater.server;

import com.fasterxml.jackson.databind.ObjectWriter;
import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import com.jyuzawa.googolplex_theater.util.JsonUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.netty.util.CharsetUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogolplexServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
  private static final Logger LOG = LoggerFactory.getLogger(GoogolplexServerHandler.class);

  private static final ObjectWriter PRETTY_PRINTER =
      JsonUtil.MAPPER.writerWithDefaultPrettyPrinter();

  private static final String MAIN_TEMPLATE = load("/main.html");
  private static final String OVERVIEW_TEMPLATE = load("/overview.html");
  private static final String REFRESH_TEMPLATE = load("/refresh.html");
  private static final byte[] FAVICON = loadBytes("/favicon.png");

  private final GoogolplexController controller;

  public GoogolplexServerHandler(GoogolplexController controller) {
    this.controller = controller;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
    ByteBuf content = ctx.alloc().buffer();
    FullHttpResponse response =
        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content);
    try {
      route(msg, response);
    } catch (Exception e) {
      LOG.error("Internal Server Error", e);
      error(response, HttpResponseStatus.INTERNAL_SERVER_ERROR);
    }
    HttpHeaders headers = response.headers();
    headers.set(HttpHeaderNames.CONTENT_LENGTH, content.writableBytes());
    headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
    headers.set(HttpHeaderNames.DATE, new Date());
    ChannelFuture writeFuture = ctx.writeAndFlush(response);
    if (!HttpUtil.isKeepAlive(msg)) {
      headers.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
      writeFuture.addListener(ChannelFutureListener.CLOSE);
    }
  }

  public void route(FullHttpRequest request, FullHttpResponse response) throws IOException {
    HttpMethod method = request.method();
    QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
    String path = queryStringDecoder.path();
    ByteBuf content = response.content();
    if (method == HttpMethod.GET) {
      if (path.equals("/")) {
        overview(content);
      } else if (path.equals("/favicon.png")) {
        content.writeBytes(FAVICON);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "image/png");
      } else {
        error(response, HttpResponseStatus.NOT_FOUND);
      }
    } else if (method == HttpMethod.POST) {
      if (path.equals("/refresh")) {
        refresh(content, queryStringDecoder.parameters());
      } else {
        error(response, HttpResponseStatus.NOT_FOUND);
      }
    } else {
      response.setStatus(HttpResponseStatus.METHOD_NOT_ALLOWED);
    }
  }

  public void overview(ByteBuf content) throws IOException {
    StringBuilder configuredDevicesContent = new StringBuilder();
    for (DeviceStatus status : controller.getConfiguredDevices()) {
      String name = status.name;
      String duration = status.duration;
      boolean disconnected = duration == null;
      if (disconnected) {
        duration = "Not Connected";
      }
      String ipAddress = status.ipAddress;

      configuredDevicesContent.append("<tr class='");
      if (ipAddress == null) {
        ipAddress = "Not Found";
      } else if (disconnected) {
        configuredDevicesContent.append("table-danger");
      }
      configuredDevicesContent.append("'><td>");
      configuredDevicesContent.append(name);
      configuredDevicesContent.append("</td><td>");
      configuredDevicesContent.append(ipAddress);
      configuredDevicesContent.append("</td><td>");
      configuredDevicesContent.append(duration);
      configuredDevicesContent.append("</td><td><code class='settings'>");
      configuredDevicesContent.append(PRETTY_PRINTER.writeValueAsString(status.settings));
      configuredDevicesContent.append("</code></td><td><form method='post' action='");
      QueryStringEncoder encoder = new QueryStringEncoder("/refresh");
      encoder.addParam("name", name);
      configuredDevicesContent.append(encoder.toString());
      configuredDevicesContent.append(
          "'><input class='btn btn-primary' type='submit' value='Refresh'");
      if (disconnected) {
        configuredDevicesContent.append(" disabled");
      }
      configuredDevicesContent.append("></form></td></tr>");
    }

    StringBuilder unconfiguredDevicesContent = new StringBuilder();
    for (DeviceStatus status : controller.getUnconfiguredDevices()) {
      unconfiguredDevicesContent.append("<tr><td>");
      unconfiguredDevicesContent.append(status.name);
      unconfiguredDevicesContent.append("</td><td>");
      unconfiguredDevicesContent.append(status.ipAddress);
      unconfiguredDevicesContent.append("</td></tr>");
    }

    String responseContent = OVERVIEW_TEMPLATE;
    responseContent =
        responseContent.replace("{{CONFIGURED_DEVICES}}", configuredDevicesContent.toString());
    responseContent =
        responseContent.replace("{{UNCONFIGURED_DEVICES}}", unconfiguredDevicesContent.toString());
    responseContent = MAIN_TEMPLATE.replace("{{CONTENT}}", responseContent);
    write(content, responseContent);
  }

  public void refresh(ByteBuf content, Map<String, List<String>> params) {
    List<String> nameParam = params.get("name");
    String name;
    if (nameParam == null) {
      name = "All devices";
      controller.refresh(null);
    } else {
      name = nameParam.get(0);
      controller.refresh(name);
    }
    String responseContent = REFRESH_TEMPLATE;
    responseContent = responseContent.replace("{{NAME}}", name);
    responseContent = MAIN_TEMPLATE.replace("{{CONTENT}}", responseContent);
    write(content, responseContent);
  }

  private static byte[] loadBytes(String resource) {
    try {
      InputStream inputStream = GoogolplexServerHandler.class.getResourceAsStream(resource);
      try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
        byte[] b = new byte[512];
        int n = 0;
        while ((n = inputStream.read(b)) != -1) {
          output.write(b, 0, n);
        }
        return output.toByteArray();
      }
    } catch (Exception e) {
      LOG.error("Failed to load resource", e);
      return null;
    }
  }

  private static String load(String resources) {
    return new String(loadBytes(resources), CharsetUtil.UTF_8);
  }

  private static void write(ByteBuf byteBuf, String string) {
    byteBuf.writeCharSequence(string, CharsetUtil.UTF_8);
  }

  private static void error(FullHttpResponse response, HttpResponseStatus status) {
    response.setStatus(status);
    response.content().writeCharSequence(status.codeAsText(), CharsetUtil.UTF_8);
  }
}
