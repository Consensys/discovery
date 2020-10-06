/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet.impl;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket.OrdinaryAuthData;
import org.ethereum.beacon.discovery.type.Bytes16;

public class OrdinaryMessageImpl extends MessagePacketImpl<OrdinaryAuthData>
    implements OrdinaryMessagePacket {

  public OrdinaryMessageImpl(
      Bytes16 maskingIV, Header<OrdinaryAuthData> header, V5Message message, Bytes gcmKey) {
    this(header, encrypt(maskingIV, header, message, gcmKey));
  }

  public OrdinaryMessageImpl(Header<OrdinaryAuthData> header, Bytes cipheredMessage) {
    super(header, cipheredMessage);
  }

  public static class OrdinaryAuthDataImpl extends AbstractBytes implements OrdinaryAuthData {
    private static final int SRC_NODE_ID_OFF = 0;
    private static final int SRC_NODE_ID_SIZE = 32;
    private static final int AUTH_DATA_SIZE = SRC_NODE_ID_OFF + SRC_NODE_ID_SIZE;

    public OrdinaryAuthDataImpl(Bytes bytes) {
      super(checkStrictSize(bytes, AUTH_DATA_SIZE));
    }

    public OrdinaryAuthDataImpl(Bytes32 sourceNodeId) {
      super(sourceNodeId);
    }

    @Override
    public Bytes32 getSourceNodeId() {
      return Bytes32.wrap(getBytes(), SRC_NODE_ID_OFF);
    }

    @Override
    public String toString() {
      return "OrdinaryAuthData{nonce=" + getSourceNodeId() + "}";
    }
  }
}
