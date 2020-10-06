/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet.impl;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HandshakeAuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.DecodeException;

public class HandshakeMessagePacketImpl extends MessagePacketImpl<HandshakeAuthData>
    implements HandshakeMessagePacket {

  public static class HandshakeAuthDataImpl extends AbstractBytes implements HandshakeAuthData {

    public static HandshakeAuthDataImpl create(
        Bytes32 srcNodeId,
        Bytes idSignature,
        Bytes ephemeralPubKey,
        Optional<NodeRecord> nodeRecord) {
      checkArgument(idSignature.size() < 256, "ID signature too large");
      checkArgument(ephemeralPubKey.size() < 256, "Ephemeral pubKey too large");
      return new HandshakeAuthDataImpl(
          Bytes.concatenate(
              srcNodeId,
              Bytes.of(idSignature.size()),
              Bytes.of(ephemeralPubKey.size()),
              idSignature,
              ephemeralPubKey,
              nodeRecord.map(NodeRecord::serialize).orElse(Bytes.EMPTY)));
    }

    public HandshakeAuthDataImpl(Bytes bytes) {
      super(checkMinSize(bytes, ID_SIG_OFF));
    }

    private static final int SRC_NODE_ID_OFF = 0;
    private static final int SRC_NODE_ID_SIZE = 32;
    private static final int SIG_SIZE_OFF = SRC_NODE_ID_OFF + SRC_NODE_ID_SIZE;
    private static final int SIG_SIZE_SIZE = 1;
    private static final int EPH_KEY_SIZE_OFF = SIG_SIZE_OFF + SIG_SIZE_SIZE;
    private static final int EPH_KEY_SIZE_SIZE = 1;
    private static final int ID_SIG_OFF = EPH_KEY_SIZE_OFF + EPH_KEY_SIZE_SIZE;

    @Override
    public Bytes32 getSourceNodeId() {
      return Bytes32.wrap(getBytes(), SRC_NODE_ID_OFF);
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
      return "HandshakeAuthData{srcNodeId="
          + getSourceNodeId()
          + ", idSignature="
          + getIdSignature()
          + ", ephemeralPubKey="
          + getEphemeralPubKey()
          + ", enrBytes="
          + getBytes().slice(getNodeRecordOff())
          + "}";
    }
  }

  public HandshakeMessagePacketImpl(Header<HandshakeAuthData> header, Bytes cipheredMessage) {
    super(header, cipheredMessage);
  }

  public HandshakeMessagePacketImpl(
      Bytes16 maskingIV, Header<HandshakeAuthData> header, V5Message message, Bytes gcmKey) {
    this(header, encrypt(maskingIV, header, message, gcmKey));
  }
}
