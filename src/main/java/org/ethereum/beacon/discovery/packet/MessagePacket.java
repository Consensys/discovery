package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.DecodeException;

public interface MessagePacket<TAuthData extends AuthData> extends Packet<TAuthData> {

  V5Message decryptMessage(Bytes key, NodeRecordFactory nodeRecordFactory);

  @Override
  default void validate() throws DecodeException {
    Packet.super.validate();
    if (getMessageBytes().isEmpty()) {
      throw new DecodeException("Message bytes are empty for message packet");
    }
  }
}
