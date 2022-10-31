/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import io.netty.buffer.Unpooled;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.net.InetSocketAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/** Netty discovery UDP client */
public class NettyDiscoveryClientImpl implements DiscoveryClient {
  private static final Logger LOG = LogManager.getLogger(NettyDiscoveryClientImpl.class);
  private NioDatagramChannel channel;

  /**
   * Constructs UDP client using
   *
   * @param outgoingStream Stream of outgoing packets, client will forward them to the channel
   * @param channel Nio channel
   */
  public NettyDiscoveryClientImpl(
      Publisher<NetworkParcel> outgoingStream, NioDatagramChannel channel) {
    this.channel = channel;
    Flux.from(outgoingStream)
        .subscribe(
            networkPacket ->
                send(networkPacket.getPacket().getBytes(), networkPacket.getDestination()));
    LOG.info("UDP discovery client started");
  }

  @Override
  public void stop() {}

  @Override
  public void send(Bytes data, InetSocketAddress destination) {
    DatagramPacket packet = new DatagramPacket(Unpooled.copiedBuffer(data.toArray()), destination);
    LOG.trace(() -> String.format("Sending packet %s", packet));
    channel.write(packet);
    channel.flush();
  }
}
