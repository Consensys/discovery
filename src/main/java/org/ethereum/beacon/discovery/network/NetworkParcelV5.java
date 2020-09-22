/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.network;

import java.net.InetSocketAddress;
import org.ethereum.beacon.discovery.packet5_1.Packet;
import org.ethereum.beacon.discovery.packet5_1.RawPacket;

public class NetworkParcelV5 implements NetworkParcel {
  private final RawPacket packet;
  private final InetSocketAddress destination;

  public NetworkParcelV5(RawPacket packet, final InetSocketAddress destination) {
    this.packet = packet;
    this.destination = destination;
  }

  @Override
  public RawPacket getPacket() {
    return packet;
  }

  @Override
  public InetSocketAddress getDestination() {
    return destination;
  }
}
