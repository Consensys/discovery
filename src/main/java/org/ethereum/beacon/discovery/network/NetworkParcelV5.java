/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import java.net.InetSocketAddress;
import java.util.Optional;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.schema.NodeRecord;

public class NetworkParcelV5 implements NetworkParcel {
  private final Packet packet;
  private final Optional<NodeRecord> nodeRecord;
  private final Optional<InetSocketAddress> replyDestination;

  public NetworkParcelV5(
      Packet packet,
      Optional<NodeRecord> nodeRecord,
      final Optional<InetSocketAddress> replyDestination) {
    this.packet = packet;
    this.nodeRecord = nodeRecord;
    this.replyDestination = replyDestination;
  }

  @Override
  public Packet getPacket() {
    return packet;
  }

  @Override
  public Optional<NodeRecord> getNodeRecord() {
    return nodeRecord;
  }

  @Override
  public Optional<InetSocketAddress> getReplyDestination() {
    return replyDestination;
  }
}
