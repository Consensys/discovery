package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;

public interface MessagePacket<TAuthData extends AuthData> extends Packet<TAuthData> {

  V5Message decryptMessage(Bytes key, NodeRecordFactory nodeRecordFactory);
}
