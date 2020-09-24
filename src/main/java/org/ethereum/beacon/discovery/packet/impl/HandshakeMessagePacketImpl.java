package org.ethereum.beacon.discovery.packet.impl;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HanshakeAuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;

public class HandshakeMessagePacketImpl extends MessagePacketImpl<HanshakeAuthData>
    implements HandshakeMessagePacket {

  public static class HandshakeAuthDataImpl extends AbstractBytes implements HanshakeAuthData {

    public static HandshakeAuthDataImpl create(
        byte version,
        Bytes12 nonce,
        Bytes idSignature,
        Bytes ephemeralPubKey,
        Optional<NodeRecord> nodeRecord) {
      checkArgument(idSignature.size() < 256, "ID signature too large");
      checkArgument(ephemeralPubKey.size() < 256, "Ephemeral pubKey too large");
      return new HandshakeAuthDataImpl(
          Bytes.concatenate(
              Bytes.of(version),
              nonce,
              Bytes.of(idSignature.size()),
              Bytes.of(ephemeralPubKey.size()),
              idSignature,
              ephemeralPubKey,
              nodeRecord.map(r -> r.serialize()).orElse(Bytes.EMPTY)));
    }

    public HandshakeAuthDataImpl(Bytes bytes) {
      super(checkMinSize(bytes, ID_SIG_OFF));
    }

    private static final int VERSION_OFF = 0;
    private static final int VERSION_SIZE = 1;
    private static final int NONCE_OFF = VERSION_SIZE;
    private static final int NONCE_SIZE = 12;
    private static final int SIG_SIZE_OFF = NONCE_OFF + NONCE_SIZE;
    private static final int SIG_SIZE_SIZE = 1;
    private static final int EPH_KEY_SIZE_OFF = SIG_SIZE_OFF + SIG_SIZE_SIZE;
    private static final int EPH_KEY_SIZE_SIZE = 1;
    private static final int ID_SIG_OFF = EPH_KEY_SIZE_OFF + EPH_KEY_SIZE_SIZE;

    @Override
    public byte getVersion() {
      return getBytes().get(VERSION_OFF);
    }

    @Override
    public Bytes12 getAesGcmNonce() {
      return Bytes12.wrap(getBytes(), NONCE_OFF);
    }

    private int getSignatureSize() {
      return getBytes().get(SIG_SIZE_OFF) & 0xFF;
    }

    private int getEphemeralPubKeySize() {
      return getBytes().get(EPH_KEY_SIZE_OFF) & 0xFF;
    }

    @Override
    public Bytes getIdSignature() {
      try {
        return getBytes().slice(ID_SIG_OFF, getSignatureSize());
      } catch (IndexOutOfBoundsException e) {
        throw new DecodeException("Handshake auth-data truncated", e);
      }
    }

    private int getEphemeralPubKeyOff() {
      return ID_SIG_OFF + getSignatureSize();
    }

    @Override
    public Bytes getEphemeralPubKey() {
      try {
        return getBytes().slice(getEphemeralPubKeyOff(), getEphemeralPubKeySize());
      } catch (IndexOutOfBoundsException e) {
        throw new DecodeException("Handshake auth-data truncated", e);
      }
    }

    private int getNodeRecordOff() {
      return getEphemeralPubKeyOff() + getEphemeralPubKeySize();
    }

    @Override
    public Optional<NodeRecord> getNodeRecord(NodeRecordFactory nodeRecordFactory) {
      try {
        Bytes nodeRecBytes = getBytes().slice(getNodeRecordOff());
        if (nodeRecBytes.isEmpty()) {
          return Optional.empty();
        } else {
          return Optional.of(nodeRecordFactory.fromBytes(nodeRecBytes));
        }
      } catch (IndexOutOfBoundsException e) {
        throw new DecodeException("Handshake auth-data truncated", e);
      } catch (Exception e) {
        throw new DecodeException("Error decoding Handshake auth-data", e);
      }
    }

    @Override
    public String toString() {
      return "HandshakeAuthData{version="
          + getVersion()
          + ", aesGcmNonce="
          + getAesGcmNonce()
          + ", idSignature="
          + getIdSignature()
          + ", ephemeralPubKey="
          + getEphemeralPubKey()
          + ", enrBytes="
          + getBytes().slice(getNodeRecordOff())
          + "}";
    }
  }

  public HandshakeMessagePacketImpl(Header<HanshakeAuthData> header, Bytes cipheredMessage) {
    super(header, cipheredMessage);
  }

  public HandshakeMessagePacketImpl(
      Header<HanshakeAuthData> header, V5Message message, Bytes gcmKey) {
    this(header, encrypt(header, message, gcmKey));
  }
}
