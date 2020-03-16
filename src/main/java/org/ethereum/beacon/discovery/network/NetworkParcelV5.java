/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import java.net.InetSocketAddress;
import org.ethereum.beacon.discovery.packet.Packet;

public class NetworkParcelV5 implements NetworkParcel {
  private final Packet packet;
  private final InetSocketAddress destination;

  public NetworkParcelV5(Packet packet, final InetSocketAddress destination) {
    this.packet = packet;
    this.destination = destination;
  }

  @Override
  public Packet getPacket() {
    return packet;
  }

  @Override
  public InetSocketAddress getDestination() {
    return destination;
  }
}
