package org.ethereum.beacon.discovery.packet5_1;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet5_1.HandshakeMessagePacket.HanshakeAuthData;
import org.ethereum.beacon.discovery.packet5_1.impl.HandshakeMessagePacketImpl;
import org.ethereum.beacon.discovery.packet5_1.impl.HandshakeMessagePacketImpl.HandshakeAuthDataImpl;
import org.ethereum.beacon.discovery.packet5_1.impl.OrdinaryMessageImpl;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;

public interface HandshakeMessagePacket extends MessagePacket<HanshakeAuthData> {

  static HandshakeMessagePacket create(Header<HanshakeAuthData> header, V5Message message, Bytes gcmKey) {
    return new HandshakeMessagePacketImpl(header, message, gcmKey);
  }


  interface HanshakeAuthData extends AuthData {

    static HanshakeAuthData create(byte version, Bytes12 nonce, Bytes idSignature,
        Bytes ephemeralPubKey, Optional<NodeRecord> nodeRecord) {
      return HandshakeAuthDataImpl.create(version, nonce, idSignature, ephemeralPubKey, nodeRecord);
    }

    byte getVersion();

    Bytes getIdSignature();

    Bytes getEphemeralPubKey();

    Optional<NodeRecord> getNodeRecord(NodeRecordFactory nodeRecordFactory);
  }
}
