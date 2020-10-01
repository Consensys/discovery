/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.packet.impl.RawPacketImpl;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.CryptoUtil;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.junit.jupiter.api.Test;

public class WhoAreYouPacketTest {
  private final Bytes32 srcNodeId =
      Bytes32.fromHexString("0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb");
  private final Bytes32 destNodeId =
      Bytes32.fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
  private final Bytes16 headerMaskingKey = Bytes16.wrap(destNodeId, 0);
  private final Bytes12 aesGcmNonce = Bytes12.fromHexString("0xffffffffffffffffffffffff");
  private final Bytes16 aesCtrIV = Bytes16.fromHexString("0x00000000000000000000000000000000");
  private final Bytes32 idNonce =
      Bytes32.fromHexString("0xdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd");

  @Test
  void checkWhoAreYouWithMessageDataFails() {
    Header<WhoAreYouAuthData> header =
        Header.createWhoAreYouHeader(srcNodeId, aesGcmNonce, idNonce, UInt64.ONE);
    Bytes extraMessageData = Bytes32.ZERO;
    RawPacketImpl rawPacket =
        new RawPacketImpl(
            Bytes.wrap(
                aesCtrIV,
                Bytes.wrap(header.encrypt(aesCtrIV, headerMaskingKey)),
                extraMessageData));

    assertThatThrownBy(
            () -> {
              rawPacket.decodePacket(headerMaskingKey);
            })
        .isInstanceOf(DecodeException.class);
  }

  @Test
  void checkTruncatedHeaderFails() {
    Header<WhoAreYouAuthData> header =
        Header.createWhoAreYouHeader(srcNodeId, aesGcmNonce, idNonce, UInt64.ONE);
    Bytes maskedHeaderBytes =
        CryptoUtil.aesctrEncrypt(headerMaskingKey, aesCtrIV, header.getBytes());

    for (int i = 1; i < maskedHeaderBytes.size(); i++) {
      RawPacketImpl rawPacket =
          new RawPacketImpl(
              Bytes.wrap(aesCtrIV, maskedHeaderBytes.slice(0, maskedHeaderBytes.size() - i)));

      assertThatThrownBy(
              () -> {
                rawPacket.decodePacket(headerMaskingKey);
              })
          .isInstanceOf(DecodeException.class);
    }
  }

  @Test
  void testPacketRoundtrip() {
    WhoAreYouPacket packet =
        WhoAreYouPacket.create(
            Header.createWhoAreYouHeader(srcNodeId, aesGcmNonce, idNonce, UInt64.ONE));
    RawPacket rawPacket = RawPacket.create(aesCtrIV, packet, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();
    Bytes expectedPacketBytes =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000088B3D4342774649980A4ADF72A8FCAA963F24B27A2F6BB44C7ED5CA10E87DE130F94D2390B9853C3ECB9A2B1E9472D43C9AE48D04689ED64E4F7CBDC7959485C06B2BDCA96AA72BF6A74A593136F5DD50DE3035E60CEB9631F9F3D8DA0B44");

    assertThat(packetBytes).isEqualTo(expectedPacketBytes);
    RawPacket rawPacket1 = RawPacket.decode(packetBytes);
    Packet<?> packet1 = rawPacket1.decodePacket(headerMaskingKey);
    assertThat(packet).isEqualTo(packet1);
  }
}
