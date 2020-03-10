/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import io.netty.buffer.Unpooled;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/** Netty discovery UDP client */
public class NettyDiscoveryClientImpl implements DiscoveryClient {
  private static final Logger logger = LogManager.getLogger(NettyDiscoveryClientImpl.class);
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
                send(
                    networkPacket.getPacket().getBytes(),
                    networkPacket.getNodeRecord(),
                    networkPacket.getReplyDestination()));
    logger.info("UDP discovery client started");
  }

  @Override
  public void stop() {}

  @Override
  public void send(
      Bytes data, Optional<NodeRecord> recipient, Optional<InetSocketAddress> destination) {
    // From discv5 spec: when responding to a request, the response should be sent to the UDP
    // envelope address of the request.
    // If that's not available (we're initiating the request) then send to the address in the
    // node record.
    InetSocketAddress address =
        destination.orElseGet(() -> getInetSocketAddressFromNodeRecord(recipient));
    DatagramPacket packet = new DatagramPacket(Unpooled.copiedBuffer(data.toArray()), address);
    logger.trace(() -> String.format("Sending packet %s", packet));
    channel.write(packet);
    channel.flush();
  }

  private InetSocketAddress getInetSocketAddressFromNodeRecord(
      final Optional<NodeRecord> recipientOptional) {
    final NodeRecord recipient =
        recipientOptional.orElseThrow(
            () -> new RuntimeException("Attempting to send new message to unknown recipient"));
    if (!recipient.getIdentityScheme().equals(IdentitySchema.V4)) {
      String error =
          String.format(
              "Accepts only V4 version of recipient's node records. Got %s instead", recipient);
      logger.error(error);
      throw new RuntimeException(error);
    }
    try {
      return new InetSocketAddress(
          InetAddress.getByAddress(((Bytes) recipient.get(EnrField.IP_V4)).toArray()), // bytes4
          (int) recipient.get(EnrField.UDP));
    } catch (UnknownHostException e) {
      String error = String.format("Failed to resolve host for node record: %s", recipient);
      logger.error(error);
      throw new RuntimeException(error);
    }
  }
}
