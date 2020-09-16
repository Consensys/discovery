package org.ethereum.beacon.discovery.packet5_1;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;

public interface HandshakeMessagePacket extends MessagePacket {

  byte getVersion();

  Bytes getIdSignature();

  Bytes getEphemeralPubKey();

  Optional<NodeRecord> getNodeRecord(NodeRecordFactory nodeRecordFactory);
}
