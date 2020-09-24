package org.ethereum.beacon.discovery.packet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HanshakeAuthData;
import org.ethereum.beacon.discovery.packet.StaticHeader.Flag;
import org.ethereum.beacon.discovery.packet.impl.HandshakeMessagePacketImpl.HandshakeAuthDataImpl;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.junit.jupiter.api.Test;

public class HandshakePacketTest {

  private final Bytes32 srcNodeId =
      Bytes32.fromHexString("0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb");
  private final Bytes32 srcStaticPrivateKey =
      Bytes32.fromHexString("0x66fb62bfbd66b9177a138c1e5cddbe4f7c30c343e94e68df8769459cb1cde628");

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
    Bytes idSignature = HanshakeAuthData.signId(idNonce, ephemeralPubKey, srcStaticPrivateKey);

    Header<HanshakeAuthData> header =
        HanshakeAuthData.createHeader(
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
    HandshakeMessagePacket packet = HandshakeMessagePacket.create(header, pingMessage, secretKey);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, packet, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    Bytes expectedPacketBytes =
        Bytes.fromHexString(
            "0x00000000000000000000000000000000088B3D4342776668980A4ADF72A8FCAA963F24B27A2F6BB44C7ED5CA10E87DE130F94D2390B9853C3DCABCD51E9472D43C9AE48D04689EF4D3D20615C33842A34E4D6ACBABF72932886E85AEC605CD2035939193AFB2EC7DC04CD960F1713C9E0D15DEC9D5A3F12F7BAA9BC6ABE3290444F9992D4BCF087514ED96706ADFF216AB862A9186875F9494150C4AE06FA4D1F0396C93F215FA4EF52417D9C40A31564E8D5F31A7F08C38045FF5E30D9661838B1EABEE9F1E561120BCC425F2D9DFD8A558DB34D3CD4DB47B9CF52821D29BA3ADE9F600A67212C8B9942797B9D9D9C5EC8639890F80416254F72F0696048B2FE3A471A21478058C07AAD5CDD251FD8792BEAEE379C245584C8453A2B22DC274A1F6881FF294B9A06B21ED2C8650BD38627111BDE9371FC41AF1E61470AA3EE045C5B7AA0638CE681A3EF94D95B84102ED931F66D2A8C8DC348104B574968A662C6A20ED83");
    assertThat(packetBytes).isEqualTo(expectedPacketBytes);

    RawPacket rawPacket1 = RawPacket.decode(packetBytes);
    HandshakeMessagePacket packet1 =
        (HandshakeMessagePacket) rawPacket1.decodePacket(headerMaskingKey);
    assertThat(packet1).isEqualTo(packet);

    PingMessage pingMessage1 =
        (PingMessage) packet1.decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
    assertThat(pingMessage1).isEqualTo(pingMessage);
  }

  @Test
  void testWrongVersionFails() {
    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(1));

    Bytes idSignature = HanshakeAuthData.signId(idNonce, ephemeralPubKey, srcStaticPrivateKey);

    HandshakeAuthDataImpl authData =
        new HandshakeAuthDataImpl(
            Bytes.concatenate(
                Bytes.of(77), // invalid version
                aesGcmNonce,
                Bytes.of(idSignature.size()),
                Bytes.of(ephemeralPubKey.size()),
                idSignature,
                ephemeralPubKey));
    Header<HanshakeAuthData> header = Header.create(srcNodeId, Flag.HANDSHAKE, authData);
    HandshakeMessagePacket packet = HandshakeMessagePacket.create(header, pingMessage, secretKey);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, packet, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    RawPacket rawPacket1 = RawPacket.decode(packetBytes);
    assertThatThrownBy(
            () -> {
              rawPacket1.decodePacket(headerMaskingKey);
            })
        .isInstanceOf(DecodeException.class);
  }

  @Test
  void testTooLargeSigSizeFails() {
    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(1));

    Bytes idSignature = HanshakeAuthData.signId(idNonce, ephemeralPubKey, srcStaticPrivateKey);

    HandshakeAuthDataImpl authData =
        new HandshakeAuthDataImpl(
            Bytes.concatenate(
                Bytes.of(HandshakeMessagePacket.HANDSHAKE_VERSION),
                aesGcmNonce,
                Bytes.of(255), // invalid sig size
                Bytes.of(ephemeralPubKey.size()),
                idSignature,
                ephemeralPubKey));
    Header<HanshakeAuthData> header = Header.create(srcNodeId, Flag.HANDSHAKE, authData);
    HandshakeMessagePacket packet = HandshakeMessagePacket.create(header, pingMessage, secretKey);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, packet, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    RawPacket rawPacket1 = RawPacket.decode(packetBytes);
    assertThatThrownBy(
            () -> {
              rawPacket1.decodePacket(headerMaskingKey);
            })
        .isInstanceOf(DecodeException.class);
  }

  @Test
  void testTooLargeEphKeySizeFails() {
    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(1));

    Bytes idSignature = HanshakeAuthData.signId(idNonce, ephemeralPubKey, srcStaticPrivateKey);

    HandshakeAuthDataImpl authData =
        new HandshakeAuthDataImpl(
            Bytes.concatenate(
                Bytes.of(HandshakeMessagePacket.HANDSHAKE_VERSION),
                aesGcmNonce,
                Bytes.of(idSignature.size()),
                Bytes.of(255), // invalid key size
                idSignature,
                ephemeralPubKey));
    Header<HanshakeAuthData> header = Header.create(srcNodeId, Flag.HANDSHAKE, authData);
    HandshakeMessagePacket packet = HandshakeMessagePacket.create(header, pingMessage, secretKey);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, packet, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    RawPacket rawPacket1 = RawPacket.decode(packetBytes);
    assertThatThrownBy(
            () -> {
              rawPacket1.decodePacket(headerMaskingKey);
            })
        .isInstanceOf(DecodeException.class);
  }
}
