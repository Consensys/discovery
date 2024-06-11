/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import io.netty.buffer.Unpooled;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/** Netty discovery UDP client */
public class NettyDiscoveryClientImpl implements DiscoveryClient {
  private static final Logger LOG = LogManager.getLogger(NettyDiscoveryClientImpl.class);

  private final Optional<NioDatagramChannel> ip4Channel;
  private final Optional<NioDatagramChannel> ip6Channel;

  /**
   * Constructs UDP client using
   *
   * @param outgoingStream Stream of outgoing packets, client will forward them to the channel
   * @param channels must be either 1 (IPv4/IPv6) or 2 (IPv4 and IPv6)
   */
  public NettyDiscoveryClientImpl(
      final Publisher<NetworkParcel> outgoingStream, final List<NioDatagramChannel> channels) {
    this.ip4Channel = channels.stream().filter(channel -> !isChannelIPv6(channel)).findFirst();
    this.ip6Channel = channels.stream().filter(this::isChannelIPv6).findFirst();
    Flux.from(outgoingStream)
        .subscribe(
            networkPacket ->
                send(networkPacket.getPacket().getBytes(), networkPacket.getDestination()));
    LOG.info("UDP discovery client started");
  }

  @Override
  public void stop() {}

  @Override
  public void send(final Bytes data, final InetSocketAddress destination) {
    final DatagramPacket packet =
        new DatagramPacket(Unpooled.copiedBuffer(data.toArray()), destination);
    final NioDatagramChannel channel;
    if (destination.getAddress() instanceof Inet6Address) {
      channel = ip6Channel.orElseThrow();
    } else {
      channel = ip4Channel.orElseThrow();
    }
    LOG.trace(() -> String.format("Sending packet %s from %s", packet, channel.localAddress()));
    channel.write(packet);
    channel.flush();
  }

  private boolean isChannelIPv6(final NioDatagramChannel channel) {
    return channel.localAddress().getAddress() instanceof Inet6Address;
  }
}
