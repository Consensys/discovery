package org.ethereum.beacon.discovery.packet5_1.impl;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet5_1.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;

public class HandshakeMessagePacketImpl extends PacketImpl implements HandshakeMessagePacket {
  private static final int VERSION_OFF = 0;
  private static final int VERSION_SIZE = 1;
  private static final int NONCE_OFF = VERSION_SIZE;
  private static final int NONCE_SIZE = 12;
  private static final int SIG_SIZE_OFF = NONCE_OFF + NONCE_SIZE;
  private static final int SIG_SIZE_SIZE = 1;
  private static final int EPH_KEY_SIZE_OFF = SIG_SIZE_OFF + SIG_SIZE_SIZE;
  private static final int EPH_KEY_SIZE_SIZE = 1;
  private static final int ID_SIG_OFF = EPH_KEY_SIZE_OFF + EPH_KEY_SIZE_SIZE;

  public HandshakeMessagePacketImpl(Header header, Bytes messageBytes) {
    super(header, messageBytes);
  }

  @Override
  public byte getVersion() {
    return getAuthData().get(VERSION_OFF);
  }

  @Override
  public Bytes12 getAesGcmNonce() {
    return Bytes12.wrap(getAuthData(), NONCE_OFF);
  }

  private byte getSignatureSize() {
    return getAuthData().get(SIG_SIZE_OFF);
  }

  private byte getEphemeralPubKeySize() {
    return getAuthData().get(EPH_KEY_SIZE_OFF);
  }

  @Override
  public Bytes getIdSignature() {
    return getAuthData().slice(ID_SIG_OFF, getSignatureSize());
  }

  private int getEphemeralPubKeyOff() {
    return ID_SIG_OFF + getSignatureSize();
  }

  @Override
  public Bytes getEphemeralPubKey() {
    return getAuthData().slice(getEphemeralPubKeyOff(), getEphemeralPubKeySize());
  }

  private int getNodeRecordOff() {
    return getEphemeralPubKeyOff() + getEphemeralPubKeySize();
  }

  @Override
  public Optional<NodeRecord> getNodeRecord(NodeRecordFactory nodeRecordFactory) {
    Bytes nodeRecBytes = getAuthData().slice(getNodeRecordOff());
    if (nodeRecBytes.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(nodeRecordFactory.fromBytes(nodeRecBytes));
    }
  }

  @Override
  public V5Message decryptMessage(Bytes key) {
    return null;
  }
}
