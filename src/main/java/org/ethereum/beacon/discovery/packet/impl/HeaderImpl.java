/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet.impl;

import static com.google.common.base.Preconditions.checkArgument;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.packet.AuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.StaticHeader;
import org.ethereum.beacon.discovery.packet.impl.HandshakeMessagePacketImpl.HandshakeAuthDataImpl;
import org.ethereum.beacon.discovery.packet.impl.OrdinaryMessageImpl.AuthDataImpl;
import org.ethereum.beacon.discovery.packet.impl.WhoAreYouPacketImpl.WhoAreYouAuthDataImpl;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.CryptoUtil;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.util.DecryptException;

public class HeaderImpl<TAUthData extends AuthData> extends AbstractBytes
    implements Header<TAUthData> {

  public static Header<?> decrypt(Bytes data, Bytes16 iv, Bytes16 destNodeId) throws DecodeException {
    try {
      checkMinSize(data, StaticHeaderImpl.STATIC_HEADER_SIZE);
      Cipher cipher = CryptoUtil.createAesctrDecryptor(destNodeId, iv);
      Bytes staticHeaderCiphered = data.slice(0, StaticHeaderImpl.STATIC_HEADER_SIZE);
      Bytes staticHeaderBytes = Bytes.wrap(cipher.update(staticHeaderCiphered.toArrayUnsafe()));
      StaticHeader header = StaticHeader.decode(staticHeaderBytes);
      header.validate();

      int authDataSize = header.getAuthDataSize();
      int headerSize = StaticHeaderImpl.STATIC_HEADER_SIZE + authDataSize;
      checkMinSize(data, headerSize);
      Bytes authDataCiphered = data.slice(StaticHeaderImpl.STATIC_HEADER_SIZE, authDataSize);
      Bytes authDataBytes = Bytes.wrap(cipher.doFinal(authDataCiphered.toArrayUnsafe()));
      AuthData authData = decodeAuthData(header, authDataBytes);
      return new HeaderImpl<>(header, authData);
    } catch (BadPaddingException | IllegalBlockSizeException e) {
      throw new DecryptException("Error decrypting packet header auth data", e);
    } catch (Exception e) {
      throw new DecodeException(
          "Couldn't decode header (iv=" + iv + ", nodeId=" + destNodeId + "): " + data, e);
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
    checkArgument(
        authData.getBytes().size() == staticHeader.getAuthDataSize(),
        "Actual authData size doesn't match header auth-data-size field");
    this.staticHeader = staticHeader;
    this.authData = authData;
  }

  @Override
  public int getSize() {
    return StaticHeaderImpl.STATIC_HEADER_SIZE + getAuthDataBytes().size();
  }

  @Override
  public StaticHeader getStaticHeader() {
    return staticHeader;
  }

  @Override
  public TAUthData getAuthData() {
    return authData;
  }

  @Override
  public Bytes encrypt(Bytes16 iv, Bytes16 destNodeId) {
    Bytes headerPlainBytes = Bytes.concatenate(staticHeader.getBytes(), getAuthDataBytes());
    return CryptoUtil.aesctrEncrypt(destNodeId, iv, headerPlainBytes);
  }

  private Bytes getAuthDataBytes() {
    return authData.getBytes();
  }

  @Override
  public String toString() {
    return "Header{header=" + staticHeader + ", authData=" + authData + "}";
  }
}
