package org.ethereum.beacon.discovery.packet5_1;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet5_1.impl.OrdinaryMessageImpl;

public interface OrdinaryMessagePacket extends MessagePacket<AuthData> {

  static OrdinaryMessagePacket create(Header<AuthData> header, V5Message message, Bytes gcmKey) {
    return new OrdinaryMessageImpl(header, message, gcmKey);
  }

  static OrdinaryMessagePacket createRandom(Header<AuthData> header, int messageSize) {
    return new OrdinaryMessageImpl(header, Bytes.random(messageSize));
  }
}
