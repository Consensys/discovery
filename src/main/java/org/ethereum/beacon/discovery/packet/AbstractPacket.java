/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;

public abstract class AbstractPacket implements Packet {
  private final Bytes bytes;

  AbstractPacket(Bytes bytes) {
    this.bytes = bytes;
  }

  @Override
  public Bytes getBytes() {
    return bytes;
  }
}
