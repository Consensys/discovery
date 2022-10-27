/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.reactivestreams.Publisher;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;

public class NettyDiscoveryServerImpl implements NettyDiscoveryServer {
  private static final Logger LOG = LogManager.getLogger(NettyDiscoveryServerImpl.class);
  private static final int RECREATION_TIMEOUT = 5000;
  private final ReplayProcessor<Envelope> incomingPackets = ReplayProcessor.cacheLast();
  private final FluxSink<Envelope> incomingSink = incomingPackets.sink();
  private final InetSocketAddress listenAddress;
  private final int trafficReadLimit; // bytes per sec
  private AtomicBoolean listen = new AtomicBoolean(false);
  private Channel channel;
  private NioEventLoopGroup nioGroup;

  public NettyDiscoveryServerImpl(InetSocketAddress listenAddress, final int trafficReadLimit) {
    this.listenAddress = listenAddress;
    this.trafficReadLimit = trafficReadLimit;
  }

  @Override
  public CompletableFuture<NioDatagramChannel> start() {
    LOG.info("Starting discovery server on UDP port {}", listenAddress.getPort());
    if (!listen.compareAndSet(false, true)) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Attempted to start an already started server"));
    }
    nioGroup = new NioEventLoopGroup(1);
    return startServer(nioGroup);
  }

  private CompletableFuture<NioDatagramChannel> startServer(final NioEventLoopGroup group) {
    CompletableFuture<NioDatagramChannel> future = new CompletableFuture<>();
    Bootstrap b = new Bootstrap();
    b.group(group)
        .channel(NioDatagramChannel.class)
        .handler(
            new ChannelInitializer<NioDatagramChannel>() {
              @Override
              public void initChannel(NioDatagramChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline
                    .addFirst(new LoggingHandler(LogLevel.TRACE))
                    .addLast(new DatagramToEnvelope())
                    .addLast(new IncomingMessageSink(incomingSink));

                if (trafficReadLimit != 0) {
                  pipeline.addFirst(new ChannelTrafficShapingHandler(0, trafficReadLimit));
                }
              }
            });

    final ChannelFuture bindFuture = b.bind(listenAddress);
    bindFuture.addListener(
        result -> {
          if (!result.isSuccess()) {
            future.completeExceptionally(result.cause());
            return;
          }

          this.channel = bindFuture.channel();
          channel
              .closeFuture()
              .addListener(
                  closeFuture -> {
                    if (!listen.get()) {
                      LOG.info("Shutting down discovery server");
                      group.shutdownGracefully();
                      return;
                    }
                    LOG.error(
                        "Discovery server closed. Trying to restore after "
                            + RECREATION_TIMEOUT
                            + " milliseconds delay",
                        closeFuture.cause());
                    Thread.sleep(RECREATION_TIMEOUT);
                    startServer(group);
                  });
          future.complete((NioDatagramChannel) this.channel);
        });
    return future;
  }

  @Override
  public Publisher<Envelope> getIncomingPackets() {
    return incomingPackets;
  }

  @Override
  public void stop() {
    if (listen.compareAndSet(true, false)) {
      LOG.info("Stopping discovery server");
      if (channel != null) {
        try {
          channel.close().sync();
        } catch (InterruptedException ex) {
          LOG.error("Failed to stop discovery server", ex);
        }
        if (nioGroup != null) {
          try {
            nioGroup.shutdownGracefully().sync();
          } catch (InterruptedException ex) {
            LOG.error("Failed to stop NIO group", ex);
          }
        }
      }
    } else {
      LOG.warn("An attempt to stop already stopping/stopped discovery server");
    }
  }
}
