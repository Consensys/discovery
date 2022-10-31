/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import reactor.core.publisher.FluxSink;

/**
 * Netty interface handler for incoming packets in form of raw bytes data wrapped as {@link Bytes}
 * Implementation forwards all incoming packets in {@link FluxSink} provided via constructor, so it
 * could be later linked to processor to form incoming messages stream
 */
public class IncomingMessageSink extends SimpleChannelInboundHandler<Envelope> {
  private static final Logger LOG = LogManager.getLogger(IncomingMessageSink.class);
  private final FluxSink<Envelope> messageSink;

  public IncomingMessageSink(FluxSink<Envelope> messageSink) {
    this.messageSink = messageSink;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Envelope msg) {
    LOG.trace(() -> String.format("Incoming packet %s in session %s", msg, ctx));
    messageSink.next(msg);
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
    LOG.error("Unexpected exception caught", cause);
  }
}
