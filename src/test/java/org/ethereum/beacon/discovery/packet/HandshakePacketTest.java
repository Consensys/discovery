/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.crypto.DefaultSigner;
import org.ethereum.beacon.discovery.crypto.Signer;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HandshakeAuthData;
import org.ethereum.beacon.discovery.packet.StaticHeader.Flag;
import org.ethereum.beacon.discovery.packet.impl.HandshakeMessagePacketImpl.HandshakeAuthDataImpl;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Test;

public class HandshakePacketTest {

  private final Bytes32 srcNodeId =
      Bytes32.fromHexString("0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb");
  private final SecretKey srcStaticPrivateKey =
      Functions.createSecretKey(
          Bytes32.fromHexString(
              "0x66fb62bfbd66b9177a138c1e5cddbe4f7c30c343e94e68df8769459cb1cde628"));

  private final Signer signer = DefaultSigner.create(srcStaticPrivateKey);

  private final Bytes32 destNodeId =
      Bytes32.fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
  private final Bytes16 headerMaskingKey = Bytes16.wrap(destNodeId, 0);
  private final Bytes12 aesGcmNonce = Bytes12.fromHexString("0xffffffffffffffffffffffff");
  private final Bytes secretKey = Bytes.fromHexString("0x00000000000000000000000000000000");
  private final Bytes16 aesCtrIV = Bytes16.fromHexString("0x00000000000000000000000000000000");
  private final Bytes32 idNonce =
      Bytes32.fromHexString("0xdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd");
  private final Bytes ephemeralPubKey =
      Bytes.fromHexString(
          "0x9A003BA6517B473FA0CD74AEFE99DADFDB34627F90FEC6362DF85803908F53A50F497889E4A9C74F48321875F8601EC65650FA0922FDA04D69089B79AF7F5533");

  @Test
  void testPacketRoundtrip() {
    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(1));
    Bytes idSignature = HandshakeAuthData.signId(idNonce, ephemeralPubKey, destNodeId, signer);

    Header<HandshakeAuthData> header =
        Header.createHandshakeHeader(
            srcNodeId,
            aesGcmNonce,
            idSignature,
            ephemeralPubKey,
            Optional.of(
                NodeRecord.fromValues(
                    IdentitySchemaInterpreter.V4,
                    UInt64.MAX_VALUE,
                    List.of(
                        new EnrField("aaaaa1", Bytes.fromHexString("0xba0bab")),
                        new EnrField("aaaaa2", Bytes.fromHexString("0xb100da"))))));
    HandshakeMessagePacket packet =
        HandshakeMessagePacket.create(aesCtrIV, header, pingMessage, secretKey);
    RawPacket rawPacket = RawPacket.createAndMask(aesCtrIV, packet, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    Bytes expectedPacketBytes =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000088B3D4342774649305F313964A39E55EA96C005AD53BB8C7560413A7008F16C9E6D2F43BBEA8814A546B7409CE783D34C4F53245D08DA4BD3F80A43DFB19F919182D7FC57D9E258ED3148B159365A1E096E1DE69016491D81BDEE4FA894DA57B3DBCA3D47C0CEFFE7387391AFD0B910D0EA8C6D22931DAD8CCE0C4BF728D82AD31947285CD738D79111E31CFD80CAE90022B933A986E592038E51E835677C38C68A251BCA7D1446DCCFF349FEBDBEDE663ACA7D7DC8863B4677BD1C2AB9DFD8A558DB34D3CD4DB47B9CF52821D29BA3ADE9F600A67212C8B9942797B9D9D9C5EC8639890F80416254F72F0696048B2FE3A471A21478058C07AAD5CDD251FD8792BEAEE379C245584C8453A2B22DC274A1F6881FF294B9A06B21ED2C86D8CA38627111BDE93766231AF1E61420180751E5E850AA0638CE3BAB0C4897B84102ED931F66D22665F8EA70F4782D77F82E15250F8D16");
    assertThat(packetBytes).isEqualTo(expectedPacketBytes);

    RawPacket rawPacket1 = RawPacket.decode(packetBytes);
    HandshakeMessagePacket packet1 =
        (HandshakeMessagePacket) rawPacket1.demaskPacket(headerMaskingKey);
    assertThat(packet1).isEqualTo(packet);

    PingMessage pingMessage1 =
        (PingMessage)
            packet1.decryptMessage(rawPacket1.getMaskingIV(), secretKey, NodeRecordFactory.DEFAULT);
    assertThat(pingMessage1).isEqualTo(pingMessage);
  }

  @Test
  void testWrongVersionFails() {
    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(1));

    Bytes idSignature = HandshakeAuthData.signId(idNonce, ephemeralPubKey, destNodeId, signer);

    HandshakeAuthDataImpl authData =
        new HandshakeAuthDataImpl(
            Bytes.concatenate(
                Bytes.of(77), // invalid version
                aesGcmNonce,
                Bytes.of(idSignature.size()),
                Bytes.of(ephemeralPubKey.size()),
                idSignature,
                ephemeralPubKey));
    Header<HandshakeAuthData> header = Header.create(Flag.HANDSHAKE, aesGcmNonce, authData);
    HandshakeMessagePacket packet =
        HandshakeMessagePacket.create(aesCtrIV, header, pingMessage, secretKey);
    RawPacket rawPacket = RawPacket.createAndMask(aesCtrIV, packet, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    RawPacket rawPacket1 = RawPacket.decode(packetBytes);
    assertThatThrownBy(() -> rawPacket1.demaskPacket(headerMaskingKey))
        .isInstanceOf(DecodeException.class);
  }

  @Test
  void testTooLargeSigSizeFails() {
    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(1));

    Bytes idSignature = HandshakeAuthData.signId(idNonce, ephemeralPubKey, destNodeId, signer);

    HandshakeAuthDataImpl authData =
        new HandshakeAuthDataImpl(
            Bytes.concatenate(
                Bytes.of(HandshakeMessagePacket.HANDSHAKE_VERSION),
                aesGcmNonce,
                Bytes.of(255), // invalid sig size
                Bytes.of(ephemeralPubKey.size()),
                idSignature,
                ephemeralPubKey));
    Header<HandshakeAuthData> header = Header.create(Flag.HANDSHAKE, aesGcmNonce, authData);
    HandshakeMessagePacket packet =
        HandshakeMessagePacket.create(aesCtrIV, header, pingMessage, secretKey);
    RawPacket rawPacket = RawPacket.createAndMask(aesCtrIV, packet, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    RawPacket rawPacket1 = RawPacket.decode(packetBytes);
    assertThatThrownBy(() -> rawPacket1.demaskPacket(headerMaskingKey))
        .isInstanceOf(DecodeException.class);
  }

  @Test
  void testTooLargeEphKeySizeFails() {
    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(1));

    Bytes idSignature = HandshakeAuthData.signId(idNonce, ephemeralPubKey, destNodeId, signer);

    HandshakeAuthDataImpl authData =
        new HandshakeAuthDataImpl(
            Bytes.concatenate(
                Bytes.of(HandshakeMessagePacket.HANDSHAKE_VERSION),
                aesGcmNonce,
                Bytes.of(idSignature.size()),
                Bytes.of(255), // invalid key size
                idSignature,
                ephemeralPubKey));
    Header<HandshakeAuthData> header = Header.create(Flag.HANDSHAKE, aesGcmNonce, authData);
    HandshakeMessagePacket packet =
        HandshakeMessagePacket.create(aesCtrIV, header, pingMessage, secretKey);
    RawPacket rawPacket = RawPacket.createAndMask(aesCtrIV, packet, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    RawPacket rawPacket1 = RawPacket.decode(packetBytes);
    assertThatThrownBy(() -> rawPacket1.demaskPacket(headerMaskingKey))
        .isInstanceOf(DecodeException.class);
  }
}
