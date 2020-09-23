package org.ethereum.beacon.discovery.packet.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.packet.StaticHeader;

public class StaticHeaderImpl extends AbstractBytes implements StaticHeader {

  private static final Charset PROTOCOL_ID_ENCODING = StandardCharsets.US_ASCII;
  private static final int PROTOCOL_ID_OFFSET = 0;
  private static final int PROTOCOL_ID_SIZE = 8;
  private static final int SOURCE_NODE_ID_OFFSET = PROTOCOL_ID_OFFSET + PROTOCOL_ID_SIZE;
  private static final int SOURCE_NODE_ID_SIZE = 32;
  private static final int FLAG_OFFSET = SOURCE_NODE_ID_OFFSET + SOURCE_NODE_ID_SIZE;
  private static final int FLAG_SIZE = 1;
  private static final int AUTH_DATA_SIZE_OFFSET = FLAG_OFFSET + FLAG_SIZE;
  private static final int AUTH_DATA_SIZE_SIZE = 2;
  static final int STATIC_HEADER_SIZE = AUTH_DATA_SIZE_OFFSET + AUTH_DATA_SIZE_SIZE;

  public static StaticHeaderImpl create(
      String protocolId, Bytes32 sourceNodeId, Flag flag, int authDataSize) {

    checkNotNull(protocolId, "protocolId");
    checkNotNull(sourceNodeId, "sourceNodeId");
    checkNotNull(flag, "flag");
    checkArgument(protocolId.length() == 8, "ProtocolId should be of length 8");
    checkArgument(authDataSize < 1 << 16, "Auth data size should be < 65536");
    Bytes headerBytes =
        Bytes.concatenate(
            Bytes.wrap(protocolId.getBytes(StaticHeaderImpl.PROTOCOL_ID_ENCODING)),
            sourceNodeId,
            Bytes.of(flag.ordinal()),
            Bytes.of(authDataSize >> 8, authDataSize & 0xFF));
    return new StaticHeaderImpl(headerBytes);
  }

  public StaticHeaderImpl(Bytes bytes) {
    super(checkStrictSize(bytes, STATIC_HEADER_SIZE));
    checkArgument(getProtocolId().equals(PROTOCOL_ID), "Failed to decrypt packet header");
  }

  @Override
  public String getProtocolId() {
    return new String(
        getBytes().slice(PROTOCOL_ID_OFFSET, PROTOCOL_ID_SIZE).toArrayUnsafe(),
        PROTOCOL_ID_ENCODING);
  }

  @Override
  public Bytes32 getSourceNodeId() {
    return Bytes32.wrap(getBytes().slice(SOURCE_NODE_ID_OFFSET, SOURCE_NODE_ID_SIZE));
  }

  @Override
  public Flag getFlag() {
    byte b = getBytes().get(FLAG_OFFSET);
    if (b >= Flag.values().length) {
      throw new DecodeException("Invalid flag value: " + b);
    }
    return Flag.values()[b];
  }

  @Override
  public int getAuthDataSize() {
    return ((0xFF & getBytes().get(AUTH_DATA_SIZE_OFFSET)) << 8)
        | (0xFF & getBytes().get(AUTH_DATA_SIZE_OFFSET + 1));
  }

  @Override
  public String toString() {
    return "{protocolId="
        + getProtocolId()
        + ", sourceNodeId="
        + getSourceNodeId()
        + ", flag="
        + getFlag()
        + ", authDataSize="
        + getAuthDataSize()
        + "}";
  }
}
