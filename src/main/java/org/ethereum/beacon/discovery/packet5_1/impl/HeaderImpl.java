package org.ethereum.beacon.discovery.packet5_1.impl;

import static com.google.common.base.Preconditions.checkArgument;

import javax.crypto.Cipher;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet5_1.AuthData;
import org.ethereum.beacon.discovery.packet5_1.Header;
import org.ethereum.beacon.discovery.packet5_1.DecodeException;
import org.ethereum.beacon.discovery.packet5_1.StaticHeader;
import org.ethereum.beacon.discovery.packet5_1.impl.HandshakeMessagePacketImpl.HandshakeAuthDataImpl;
import org.ethereum.beacon.discovery.packet5_1.impl.OrdinaryMessageImpl.AuthDataImpl;
import org.ethereum.beacon.discovery.packet5_1.impl.WhoAreYouPacketImpl.WhoAreYouAuthDataImpl;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.CryptoUtil;

public class HeaderImpl<TAUthData extends AuthData> extends AbstractBytes
    implements Header<TAUthData> {

  public static Header<?> decrypt(Bytes data, Bytes16 iv, Bytes16 peerId) throws DecodeException {
    try {
      checkMinSize(data, StaticHeaderImpl.STATIC_HEADER_SIZE);
      Cipher cipher = CryptoUtil.createAesctrDecryptor(peerId, iv);
      Bytes staticHeaderCiphered = data.slice(0, StaticHeaderImpl.STATIC_HEADER_SIZE);
      Bytes staticHeaderBytes = Bytes.wrap(
          cipher.update(staticHeaderCiphered.toArrayUnsafe()));
      StaticHeader header = StaticHeader.decode(staticHeaderBytes);

      int authDataSize = header.getAuthDataSize();
      int headerSize = StaticHeaderImpl.STATIC_HEADER_SIZE + authDataSize;
      checkMinSize(data, headerSize);
      Bytes authDataCiphered = data.slice(StaticHeaderImpl.STATIC_HEADER_SIZE, authDataSize);
      Bytes authDataBytes = Bytes.wrap(
          cipher.doFinal(authDataCiphered.toArrayUnsafe()));
      AuthData authData = decodeAuthData(header, authDataBytes);
      return new HeaderImpl<>(header, authData);
    } catch (Exception e) {
      throw new DecodeException(
          "Couldn't decode header (iv=" + iv + ", peerId=" + peerId + "): " + data, e);
    }
  }

  private static AuthData decodeAuthData(StaticHeader header, Bytes authDataBytes) {
    switch (header.getFlag()) {
      case WHOAREYOU:
        return new WhoAreYouAuthDataImpl(authDataBytes);
      case HANDSHAKE:
        return new HandshakeAuthDataImpl(authDataBytes);
      case MESSAGE:
        return new AuthDataImpl(authDataBytes);
      default:
        throw new DecodeException("Unknown flag: " + header.getFlag());
    }
  }

  private final StaticHeader staticHeader;
  private final TAUthData authData;

  public HeaderImpl(StaticHeader staticHeader, TAUthData authData) {
    super(Bytes.wrap(staticHeader.getBytes(), authData.getBytes()));
    checkArgument(authData.getBytes().size() == staticHeader.getAuthDataSize(),
        "Actual authData size doesn't match header auth-data-size field");
    this.staticHeader = staticHeader;
    this.authData = authData;
  }

  public int getSize() {
    return StaticHeaderImpl.STATIC_HEADER_SIZE + getAuthDataBytes().size();
  }

  public StaticHeader getStaticHeader() {
    return staticHeader;
  }

  @Override
  public TAUthData getAuthData() {
    return authData;
  }

  public Bytes encrypt(Bytes16 iv, Bytes16 peerId) {
    Bytes headerPlainBytes = Bytes.concatenate(staticHeader.getBytes(), getAuthDataBytes());
    return CryptoUtil.aesctrEncrypt(peerId, iv, headerPlainBytes);
  }

  private Bytes getAuthDataBytes() {
    return authData.getBytes();
  }

  @Override
  public String toString() {
    return "Header[header=" + staticHeader + ", authData=" + authData + "]";
  }
}
