/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.StaticHeader;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes2;

public class StaticHeaderImpl extends AbstractBytes implements StaticHeader {

  private static final Charset PROTOCOL_ID_ENCODING = StandardCharsets.US_ASCII;
  private static final int PROTOCOL_ID_OFFSET = 0;
  private static final int PROTOCOL_ID_SIZE = 6;
  private static final int VERSION_OFFSET = PROTOCOL_ID_OFFSET + PROTOCOL_ID_SIZE;
  private static final int VERSION_SIZE = 2;
  private static final int FLAG_OFFSET = VERSION_OFFSET + VERSION_SIZE;
  private static final int FLAG_SIZE = 1;
  private static final int NONCE_OFFSET = FLAG_OFFSET + FLAG_SIZE;
  private static final int NONCE_SIZE = 12;
  private static final int AUTH_DATA_SIZE_OFFSET = NONCE_OFFSET + NONCE_SIZE;
  private static final int AUTH_DATA_SIZE_SIZE = 2;
  static final int STATIC_HEADER_SIZE = AUTH_DATA_SIZE_OFFSET + AUTH_DATA_SIZE_SIZE;

  public static StaticHeaderImpl create(
      String protocolId, Bytes2 version, Flag flag, Bytes12 nonce, int authDataSize) {

    checkNotNull(protocolId, "protocolId");
    checkNotNull(flag, "flag");
    checkNotNull(nonce, "nonce");
    checkArgument(protocolId.length() == 6, "ProtocolId should be of length 6");
    checkArgument(authDataSize < 1 << 16, "Auth data size should be < 65536");
    Bytes headerBytes =
        Bytes.concatenate(
            Bytes.wrap(protocolId.getBytes(StaticHeaderImpl.PROTOCOL_ID_ENCODING)),
            version,
            Bytes.of(flag.getCode()),
            nonce,
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
  public Bytes2 getVersion() {
    return Bytes2.wrap(getBytes().slice(VERSION_OFFSET, VERSION_SIZE));
  }

  @Override
  public Flag getFlag() {
    return Flag.fromCode(getBytes().get(FLAG_OFFSET));
  }

  @Override
  public Bytes12 getNonce() {
    return Bytes12.wrap(getBytes().slice(NONCE_OFFSET, NONCE_SIZE));
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
        + ", version="
        + getVersion()
        + ", flag="
        + getFlag()
        + ", nonce="
        + getNonce()
        + ", authDataSize="
        + getAuthDataSize()
        + "}";
  }
}
