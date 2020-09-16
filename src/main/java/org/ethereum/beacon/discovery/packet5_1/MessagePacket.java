package org.ethereum.beacon.discovery.packet5_1;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.type.Bytes12;

public interface MessagePacket extends Packet {

  Bytes getMessageBytes();

  V5Message decryptMessage(Bytes key);
}
