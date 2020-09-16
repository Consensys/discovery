package org.ethereum.beacon.discovery.packet5_1.impl;

import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.packet5_1.Packet.Flag;
import org.ethereum.beacon.discovery.packet5_1.PacketDecodeException;

class StaticHeader extends AbstractPacket {

  private static final int PROTOCOL_ID_OFFSET = 0;
  private static final int PROTOCOL_ID_SIZE = 8;
  private static final int SOURCE_PEER_ID_OFFSET = PROTOCOL_ID_OFFSET + PROTOCOL_ID_SIZE;
  private static final int SOURCE_PEER_ID_SIZE = 32;
  private static final int FLAG_OFFSET = SOURCE_PEER_ID_OFFSET + SOURCE_PEER_ID_SIZE;
  private static final int FLAG_SIZE = 1;
  private static final int AUTH_DATA_SIZE_OFFSET = FLAG_OFFSET + FLAG_SIZE;
  private static final int AUTH_DATA_SIZE_SIZE = 2;
  static final int STATIC_HEADER_SIZE = AUTH_DATA_SIZE_OFFSET + AUTH_DATA_SIZE_SIZE;


  public StaticHeader(Bytes bytes) {
    super(bytes, STATIC_HEADER_SIZE);
  }

  public String getProtocolId() {
    return new String(getBytes().slice(PROTOCOL_ID_OFFSET, PROTOCOL_ID_SIZE).toArrayUnsafe(),
        StandardCharsets.US_ASCII);
  }

  public Bytes32 getSourcePeerId() {
    return Bytes32.wrap(getBytes().slice(SOURCE_PEER_ID_OFFSET, SOURCE_PEER_ID_SIZE));
  }

  public Flag getFlag() {
    byte b = getBytes().get(FLAG_OFFSET);
    if (b >= Flag.values().length) {
      throw new PacketDecodeException("Invalid flag value: " + b);
    }
    return Flag.values()[b];
  }

  public int getAuthDataSize() {
    return 0xFF & getBytes().get(AUTH_DATA_SIZE_OFFSET) << 8 |
        getBytes().get(AUTH_DATA_SIZE_OFFSET + 1);
  }
}
