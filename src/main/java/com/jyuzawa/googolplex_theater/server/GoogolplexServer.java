package com.jyuzawa.googolplex_theater.server;

import com.jyuzawa.googolplex_theater.client.GoogolplexController;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import java.io.Closeable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class runs the server for the web UI.
 *
 * @author jyuzawa
 */
public final class GoogolplexServer implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(GoogolplexServer.class);

  public static final int DEFAULT_PORT = 8000;

  private final EventLoopGroup bossGroup;

  public GoogolplexServer(GoogolplexController controller, int port) throws InterruptedException {
    this.bossGroup = new NioEventLoopGroup(1);
    ServerBootstrap serverBootstrap = new ServerBootstrap();
    serverBootstrap
        .group(bossGroup, controller.getEventLoopGroup())
        .channel(NioServerSocketChannel.class)
        .childHandler(
            new ChannelInitializer<SocketChannel>() {
              @Override
              public void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline p = ch.pipeline();
                p.addLast("codec", new HttpServerCodec());
                p.addLast("decompressor", new HttpContentDecompressor());
                p.addLast("compressor", new HttpContentCompressor());
                p.addLast("aggregator", new HttpObjectAggregator(1024 * 1024));
                p.addLast("handler", new GoogolplexServerHandler(controller));
              }
            });
    serverBootstrap.bind(port).sync();
    LOG.info("Running server on port " + port);
  }

  @Override
  public void close() {
    bossGroup.shutdownGracefully();
  }
}
