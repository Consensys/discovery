/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.impl.OrdinaryMessageImpl;

public interface OrdinaryMessagePacket extends MessagePacket<AuthData> {

  static OrdinaryMessagePacket create(Header<AuthData> header, V5Message message, Bytes gcmKey) {
    return new OrdinaryMessageImpl(header, message, gcmKey);
  }

  static OrdinaryMessagePacket createRandom(Header<AuthData> header, int messageSize) {
    return new OrdinaryMessageImpl(header, Bytes.random(messageSize));
  }
}
