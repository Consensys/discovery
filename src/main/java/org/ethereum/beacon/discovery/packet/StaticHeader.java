/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import java.util.Arrays;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.impl.StaticHeaderImpl;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes2;
import org.ethereum.beacon.discovery.util.DecodeException;

/**
 * Static part of {@link Packet}'s {@link Header}
 * {@code static-header = protocol-id || version || flag || nonce || authdata-size}
 */
public interface StaticHeader extends BytesSerializable {

  String PROTOCOL_ID = "discv5";
  Bytes2 VERSION = Bytes2.fromHexString("0x0001");

  static StaticHeader decode(Bytes staticHeaderBytes) {
    return new StaticHeaderImpl(staticHeaderBytes);
  }

  String getProtocolId();

  Bytes2 getVersion();

  Flag getFlag();

  Bytes12 getNonce();

  int getAuthDataSize();

  @Override
  default void validate() {
    if (!getProtocolId().equals(PROTOCOL_ID)) {
      throw new DecodeException("Invalid protocolId field: '" + getProtocolId() + "'");
    }
    if (!getVersion().equals(VERSION)) {
      throw new DecodeException("Invalid version: " + getVersion());
    }
    DecodeException.wrap(
        () -> "Couldn't decode static header: " + getBytes(),
        () -> {
          getFlag();
          getNonce();
          getAuthDataSize();
        });
  }

  enum Flag {
    MESSAGE(0),
    WHOAREYOU(1),
    HANDSHAKE(2);

    public static Flag fromCode(int code) throws DecodeException {
      return Arrays.stream(Flag.values())
          .filter(v -> v.getCode() == code)
          .findFirst()
          .orElseThrow(() -> new DecodeException("Invalid packet flag code: " + code));
    }

    private final int code;

    Flag(int code) {
      this.code = code;
    }

    public int getCode() {
      return code;
    }
  }
}
