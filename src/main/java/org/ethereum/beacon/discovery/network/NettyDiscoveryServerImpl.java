/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.reactivestreams.Publisher;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;

public class NettyDiscoveryServerImpl implements NettyDiscoveryServer {
  private static final int RECREATION_TIMEOUT = 5000;
  private static final int STOPPING_TIMEOUT = 10000;
  private static final Logger logger = LogManager.getLogger(NettyDiscoveryServerImpl.class);
  private final ReplayProcessor<Envelope> incomingPackets = ReplayProcessor.cacheLast();
  private final FluxSink<Envelope> incomingSink = incomingPackets.sink();
  private final Integer udpListenPort;
  private final String udpListenHost;
  private AtomicBoolean listen = new AtomicBoolean(false);
  private Channel channel;

  public NettyDiscoveryServerImpl(Bytes udpListenHost, Integer udpListenPort) { // bytes4
    try {
      this.udpListenHost = InetAddress.getByAddress(udpListenHost.toArray()).getHostAddress();
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
    this.udpListenPort = udpListenPort;
  }

  @Override
  public CompletableFuture<NioDatagramChannel> start() {
    logger.info(String.format("Starting discovery server on UDP port %s", udpListenPort));
    if (!listen.compareAndSet(false, true)) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Attempted to start an already started server"));
    }
    NioEventLoopGroup group = new NioEventLoopGroup(1);
    return startServer(group);
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
                ch.pipeline()
                    .addFirst(new LoggingHandler(LogLevel.TRACE))
                    .addLast(new DatagramToEnvelope())
                    .addLast(new IncomingMessageSink(incomingSink));
              }
            });

    final ChannelFuture bindFuture = b.bind(udpListenHost, udpListenPort);
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
                      logger.info("Shutting down discovery server");
                      group.shutdownGracefully().sync();
                      return;
                    }
                    logger.error(
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
      logger.info("Stopping discovery server");
      if (channel != null) {
        try {
          channel.close().await(STOPPING_TIMEOUT);
        } catch (InterruptedException ex) {
          logger.error("Failed to stop discovery server", ex);
        }
      }
    } else {
      logger.warn("An attempt to stop already stopping/stopped discovery server");
    }
  }
}
