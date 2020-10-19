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
  private final Bytes32 destNodeId =
      Bytes32.fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
  private final Bytes16 headerMaskingKey = Bytes16.wrap(destNodeId, 0);
  private final Bytes12 aesGcmNonce = Bytes12.fromHexString("0xffffffffffffffffffffffff");
  private final Bytes16 aesCtrIV = Bytes16.fromHexString("0x00000000000000000000000000000000");
  private final Bytes16 idNonce = Bytes16.fromHexString("0xdddddddddddddddddddddddddddddddd");

  @Test
  void checkWhoAreYouWithMessageDataFails() {
    Header<WhoAreYouAuthData> header =
        Header.createWhoAreYouHeader(aesGcmNonce, idNonce, UInt64.ONE);
    Bytes extraMessageData = Bytes32.ZERO;
    RawPacketImpl rawPacket =
        new RawPacketImpl(
            Bytes.wrap(
                aesCtrIV,
                RawPacketImpl.maskHeader(header, aesCtrIV, headerMaskingKey),
                extraMessageData));

    assertThatThrownBy(
            () -> {
              rawPacket.demaskPacket(headerMaskingKey);
            })
        .isInstanceOf(DecodeException.class);
  }

  @Test
  void checkTruncatedHeaderFails() {
    Header<WhoAreYouAuthData> header =
        Header.createWhoAreYouHeader(aesGcmNonce, idNonce, UInt64.ONE);
    Bytes maskedHeaderBytes =
        CryptoUtil.aesctrEncrypt(headerMaskingKey, aesCtrIV, header.getBytes());

    for (int i = 1; i < maskedHeaderBytes.size(); i++) {
      RawPacketImpl rawPacket =
          new RawPacketImpl(
              Bytes.wrap(aesCtrIV, maskedHeaderBytes.slice(0, maskedHeaderBytes.size() - i)));

      assertThatThrownBy(
              () -> {
                rawPacket.demaskPacket(headerMaskingKey);
              })
          .isInstanceOf(DecodeException.class);
    }
  }

  @Test
  void testPacketRoundtrip() {
    WhoAreYouPacket packet =
        WhoAreYouPacket.create(Header.createWhoAreYouHeader(aesGcmNonce, idNonce, UInt64.ONE));
    RawPacket rawPacket = RawPacket.createAndMask(aesCtrIV, packet, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();
    Bytes expectedPacketBytes =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000088B3D4342774649335F313964A39E55EA96C005AD5286FB0239850E59482C3215ABBAB61BC2C7873FCBAED4E16B8C");

    assertThat(packetBytes).isEqualTo(expectedPacketBytes);
    RawPacket rawPacket1 = RawPacket.decode(packetBytes);
    Packet<?> packet1 = rawPacket1.demaskPacket(headerMaskingKey);
    assertThat(packet).isEqualTo(packet1);
  }
}
