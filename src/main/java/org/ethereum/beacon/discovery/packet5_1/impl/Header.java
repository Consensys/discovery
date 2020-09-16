package org.ethereum.beacon.discovery.packet5_1.impl;

import static org.ethereum.beacon.discovery.packet5_1.impl.AbstractPacket.checkSize;

import javax.crypto.Cipher;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet5_1.PacketDecodeException;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.CryptoUtil;

public class Header extends AbstractPacket{

  public static Header decode(Bytes data, Bytes16 iv, Bytes16 peerId) throws PacketDecodeException {
    try {
      checkSize(data, StaticHeader.STATIC_HEADER_SIZE);
      Cipher cipher = CryptoUtil.createAesctrDecryptor(peerId, iv);
      Bytes staticHeaderCiphered = data.slice(0, StaticHeader.STATIC_HEADER_SIZE);
      Bytes staticHeaderBytes = Bytes.wrap(
          cipher.update(staticHeaderCiphered.toArrayUnsafe()));
      StaticHeader header = new StaticHeader(staticHeaderBytes);

      int authDataSize = header.getAuthDataSize();
      int headerSize = StaticHeader.STATIC_HEADER_SIZE + authDataSize;
      checkSize(data, headerSize);
      Bytes authDataCiphered = data.slice(StaticHeader.STATIC_HEADER_SIZE, authDataSize);
      Bytes authDataBytes = Bytes.wrap(
          cipher.doFinal(authDataCiphered.toArrayUnsafe()));
      return new Header(header, authDataBytes);
    } catch (Exception e) {
      throw new PacketDecodeException(
          "Couldn't decode header (iv=" + iv + ", peerId=" + peerId + "): " + data, e);
    }
  }

  private final StaticHeader staticHeader;
  private final Bytes authDataBytes;

  public Header(StaticHeader staticHeader, Bytes authDataBytes) {
    super(Bytes.wrap(staticHeader.getBytes(), authDataBytes), StaticHeader.STATIC_HEADER_SIZE);
    this.staticHeader = staticHeader;
    this.authDataBytes = authDataBytes;
  }

  public int getSize() {
    return StaticHeader.STATIC_HEADER_SIZE + getAuthDataBytes().size();
  }

  public StaticHeader getStaticHeader() {
    return staticHeader;
  }

  public Bytes getAuthDataBytes() {
    return authDataBytes;
  }
}
