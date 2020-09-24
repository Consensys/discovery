package org.ethereum.beacon.discovery.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.ID_SIGNATURE_PREFIX;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.AuthData;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HanshakeAuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.RawPacket;
import org.ethereum.beacon.discovery.packet.StaticHeader;
import org.ethereum.beacon.discovery.packet.StaticHeader.Flag;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.CryptoUtil;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SanityTestVectors {

  @Test
  void simpleEncodeDecode() {
    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x01"), UInt64.ZERO);
    AuthData authData = AuthData.create(Bytes12.fromHexString("0x050505050505050505050505"));
    StaticHeader staticHeader =
        StaticHeader.create(
            Bytes32.fromHexString(
                "0x0303030303030303030303030303030303030303030303030303030303030303"),
            Flag.MESSAGE,
            authData.getBytes().size());
    Header<AuthData> header = Header.create(staticHeader, authData);
    Bytes key = Bytes.fromHexString("0x01010101010101010101010101010101");
    OrdinaryMessagePacket packet = OrdinaryMessagePacket.create(header, pingMessage, key);
    Bytes16 destPeerId =
        Bytes16.wrap(
            Bytes.fromHexString(
                "0x0404040404040404040404040404040404040404040404040404040404040404"),
            0);
    RawPacket rawPacket =
        RawPacket.create(
            Bytes16.fromHexString("0x02020202020202020202020202020202"), packet, destPeerId);
    System.out.println(rawPacket.getBytes());

    RawPacket packet1 = RawPacket.decode(rawPacket.getBytes());
    Packet<?> packet2 = packet1.decodePacket(destPeerId);
    OrdinaryMessagePacket messagePacket = (OrdinaryMessagePacket) packet2;
    V5Message v5Message = messagePacket.decryptMessage(key, NodeRecordFactory.DEFAULT);

    Assertions.assertEquals(pingMessage, v5Message);
  }

  @Test
  void v1() {
    // WHOAREYOU packet (flag 1):
    // # src-node-id = 0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb
    // # dest-node-id = 0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9
    // # whoareyou.request-nonce = 0x0102030405060708090a0b0c
    // # whoareyou.id-nonce = 0x0102030405060708090a0b0c0d0e0f1000000000000000000000000000000000
    // # whoareyou.enr-seq = 0
    //
    // 00000000000000000000000000000000088b3d4342776668980a4adf72a8fcaa
    // 963f24b27a2f6bb44c7ed5ca10e87de130f94d2390b9853c3ecb9ad5e368892e
    // c562137bf19c6d0a9191a5651c4f415117bdfa0c7ab86af62b7a9784eceb2800
    // 8d03ede83bd1369631f9f3d8da0b45

    WhoAreYouAuthData authData =
        WhoAreYouAuthData.create(
            Bytes12.fromHexString("0x0102030405060708090a0b0c"),
            Bytes32.fromHexString(
                "0x0102030405060708090a0b0c0d0e0f1000000000000000000000000000000000"),
            UInt64.ZERO);
    Header<WhoAreYouAuthData> header =
        Header.create(
            Bytes32.fromHexString(
                "0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb"),
            Flag.WHOAREYOU,
            authData);
    WhoAreYouPacket packet = WhoAreYouPacket.create(header);
    Bytes32 destNodeId =
        Bytes32.fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
    RawPacket rawPacket =
        RawPacket.create(
            Bytes16.fromHexString("00000000000000000000000000000000"),
            packet,
            Bytes16.wrap(destNodeId, 0));
    Bytes rawPacketBytes = rawPacket.getBytes();

    Bytes expectedBytes =
        Bytes.fromHexString(
            "00000000000000000000000000000000088b3d4342776668980a4adf72a8fcaa"
                + "963f24b27a2f6bb44c7ed5ca10e87de130f94d2390b9853c3ecb9ad5e368892e"
                + "c562137bf19c6d0a9191a5651c4f415117bdfa0c7ab86af62b7a9784eceb2800"
                + "8d03ede83bd1369631f9f3d8da0b45");

    assertThat(rawPacketBytes).isEqualTo(expectedBytes);

    RawPacket rawPacket1 = RawPacket.decode(rawPacketBytes);
    assertThat(rawPacket1.getIV()).isEqualTo(rawPacket.getIV());

    WhoAreYouPacket packet1 =
        (WhoAreYouPacket) rawPacket1.decodePacket(Bytes16.wrap(destNodeId, 0));
    assertThat(packet1.getMessageBytes().isEmpty()).isTrue();
    Header<WhoAreYouAuthData> header1 = packet1.getHeader();
    WhoAreYouAuthData authData1 = header1.getAuthData();
    assertThat(authData1.getBytes()).isEqualTo(authData.getBytes());
    assertThat(authData1.isEqual(authData)).isTrue();
    assertThat(header1.getStaticHeader().isEqual(header.getStaticHeader())).isTrue();
  }

  @Test
  void v2() {
    // Ping message packet (flag 0):
    //
    // # src-node-id = 0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb
    // # dest-node-id = 0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9
    // # nonce = 0xffffffffffffffffffffffff
    // # read-key = 0x00000000000000000000000000000000
    // # ping.req-id = 0x00000001
    // # ping.enr-seq = 2
    //
    // 00000000000000000000000000000000088b3d4342776668980a4adf72a8fcaa
    // 963f24b27a2f6bb44c7ed5ca10e87de130f94d2390b9853c3fcba22b1e9472d4
    // 3c9ae48d04689eb84102ed931f66d180cbb4219f369a24f4e6b24d7bdc2a04

    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(2));
    AuthData authData = AuthData.create(Bytes12.fromHexString("0xffffffffffffffffffffffff"));
    Header<AuthData> header =
        Header.create(
            Bytes32.fromHexString(
                "0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb"),
            Flag.MESSAGE,
            authData);
    Bytes key = Bytes.fromHexString("0x00000000000000000000000000000000");
    OrdinaryMessagePacket packet = OrdinaryMessagePacket.create(header, pingMessage, key);
    Bytes16 destNodeId =
        Bytes16.wrap(
            Bytes.fromHexString(
                "0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9"),
            0);

    RawPacket rawPacket =
        RawPacket.create(
            Bytes16.fromHexString("0x00000000000000000000000000000000"), packet, destNodeId);
    Bytes rawPacketBytes = rawPacket.getBytes();

    Bytes expectedBytes =
        Bytes.fromHexString(
            "00000000000000000000000000000000088b3d4342776668980a4adf72a8fcaa"
                + "963f24b27a2f6bb44c7ed5ca10e87de130f94d2390b9853c3fcba22b1e9472d4"
                + "3c9ae48d04689eb84102ed931f66d180cbb4219f369a24f4e6b24d7bdc2a04");

    assertThat(rawPacketBytes).isEqualTo(expectedBytes);

    RawPacket rawPacket1 = RawPacket.decode(rawPacketBytes);
    assertThat(rawPacket1.getIV()).isEqualTo(rawPacket.getIV());

    OrdinaryMessagePacket packet1 = (OrdinaryMessagePacket) rawPacket1.decodePacket(destNodeId);
    assertThat(packet1.getMessageBytes()).isEqualTo(packet.getMessageBytes());
    PingMessage pingMessage1 = (PingMessage) packet1.decryptMessage(key, NodeRecordFactory.DEFAULT);
    assertThat(pingMessage1).isEqualTo(pingMessage);

    Header<AuthData> header1 = packet1.getHeader();
    AuthData authData1 = header1.getAuthData();
    assertThat(authData1.getBytes()).isEqualTo(authData.getBytes());
    assertThat(authData1.isEqual(authData)).isTrue();
    assertThat(header1.getStaticHeader().isEqual(header.getStaticHeader())).isTrue();
  }

  @Test
  void v3() {
    // # src-node-id = 0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb
    // # dest-node-id = 0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9
    // # nonce = 0xffffffffffffffffffffffff
    // # read-key = 0x4917330b5aeb51650213f90d5f253c45
    // # ping.req-id = 0x00000001
    // # ping.enr-seq = 1
    // #
    // # handshake inputs:
    // #
    // # whoareyou.request-nonce = 0x0102030405060708090a0b0c
    // # whoareyou.id-nonce = 0x0102030405060708090a0b0c0d0e0f1000000000000000000000000000000000
    // # whoareyou.enr-seq = 1
    // # ephemeral-key = 0x0288ef00023598499cb6c940146d050d2b1fb914198c327f76aad590bead68b6
    // # ephemeral-pubkey = 0x039a003ba6517b473fa0cd74aefe99dadfdb34627f90fec6362df85803908f53a5
    //
    // 00000000000000000000000000000000088b3d4342776668980a4adf72a8fcaa
    // 963f24b27a2f6bb44c7ed5ca10e87de130f94d2390b9853c3dcb21d51e9472d4
    // 3c9ae48d04689ef4d3d2602a5e89ac340f9e81e722b1d7dac2578d520dd5bc6d
    // c1e38ad3ab33012be1a5d259267a0947bf242219834c5702d1c694c0ceb4a6a2
    // 7b5d68bd2c2e32e6cb9696706adff216ab862a9186875f9494150c4ae06fa4d1
    // f0396c93f215fa4ef52417d9c40a31564e8d5f31a7f08c38045ff5e30d966183
    // 8b1eabee9f1e561120bc7fccc3d4569a69fdf04f31230ae4be20404467d9ea9a
    // b3cd
    WhoAreYouAuthData whoAreYouAuthData =
        WhoAreYouAuthData.create(
            Bytes12.fromHexString("0x0102030405060708090a0b0c"),
            Bytes32.fromHexString(
                "0x0102030405060708090a0b0c0d0e0f1000000000000000000000000000000000"),
            UInt64.valueOf(1));
    Header<WhoAreYouAuthData> whoAreYouHeader =
        Header.create(
            Bytes32.fromHexString(
                "0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb"),
            Flag.WHOAREYOU,
            whoAreYouAuthData);
    Bytes ephemeralPrivKey =
        Bytes.fromHexString("0x0288ef00023598499cb6c940146d050d2b1fb914198c327f76aad590bead68b6");
    //    Bytes ephemeralPubKey = Functions.derivePublicKeyFromPrivate(ephemeralPrivKey);

    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(1));
    Bytes ephemeralPubKey =
        Bytes.fromHexString(
            "0x9A003BA6517B473FA0CD74AEFE99DADFDB34627F90FEC6362DF85803908F53A50F497889E4A9C74F48321875F8601EC65650FA0922FDA04D69089B79AF7F5533");
    Bytes idSignatureInput =
        CryptoUtil.sha256(
            Bytes.wrap(
                ID_SIGNATURE_PREFIX,
                whoAreYouHeader.getAuthData().getIdNonce(),
                ephemeralPubKey));
    Bytes idSignature =
        Functions.sign(
            Bytes.fromHexString(
                "0x66fb62bfbd66b9177a138c1e5cddbe4f7c30c343e94e68df8769459cb1cde628"),
            //
            // Bytes.fromHexString("0xeef77acb6c6a6eebc5b363a475ac583ec7eccdb42b6481424c60f59aa326547f"),
            idSignatureInput);

    HanshakeAuthData authData =
        HanshakeAuthData.create(
            Bytes12.fromHexString("0xFFFFFFFFFFFFFFFFFFFFFFFF"),
            idSignature,
            //            Bytes.fromHexString(
            //
            // "0xC14A44C1E56C122877E65606AD2CE92D1AD6E13E946D4CE0673B90E237BDD05C2181FC714C008686A08EB4DF52FAAB7614A469576E9AB1363377A7DE100AEDC2"),
            //            ),
            ephemeralPubKey,
            Optional.empty());
    Header<HanshakeAuthData> header =
        Header.create(
            Bytes32.fromHexString(
                "0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb"),
            Flag.HANDSHAKE,
            authData);
    Bytes key = Bytes.fromHexString("0x4917330b5aeb51650213f90d5f253c45");
    HandshakeMessagePacket packet = HandshakeMessagePacket.create(header, pingMessage, key);
    Bytes16 destNodeId =
        Bytes16.wrap(
            Bytes.fromHexString(
                "0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9"),
            0);
    RawPacket rawPacket =
        RawPacket.create(
            Bytes16.fromHexString("0x00000000000000000000000000000000"), packet, destNodeId);

    Bytes rawPacketBytes = rawPacket.getBytes();
    Bytes expectedBytes =
        Bytes.fromHexString(
            "00000000000000000000000000000000088b3d4342776668980a4adf72a8fcaa"
                + "963f24b27a2f6bb44c7ed5ca10e87de130f94d2390b9853c3dcb21d51e9472d4"
                + "3c9ae48d04689ef4d3d2602a5e89ac340f9e81e722b1d7dac2578d520dd5bc6d"
                + "c1e38ad3ab33012be1a5d259267a0947bf242219834c5702d1c694c0ceb4a6a2"
                + "7b5d68bd2c2e32e6cb9696706adff216ab862a9186875f9494150c4ae06fa4d1"
                + "f0396c93f215fa4ef52417d9c40a31564e8d5f31a7f08c38045ff5e30d966183"
                + "8b1eabee9f1e561120bc7fccc3d4569a69fdf04f31230ae4be20404467d9ea9a"
                + "b3cd");

    assertThat(rawPacketBytes).isEqualTo(expectedBytes);

    RawPacket rawPacket1 = RawPacket.decode(expectedBytes);
    HandshakeMessagePacket packet1 = (HandshakeMessagePacket) rawPacket1.decodePacket(destNodeId);
    assertThat(packet1.getMessageBytes()).isEqualTo(packet.getMessageBytes());
    PingMessage pingMessage1 = (PingMessage) packet1.decryptMessage(key, NodeRecordFactory.DEFAULT);
    assertThat(pingMessage1).isEqualTo(pingMessage);

    Header<HanshakeAuthData> header1 = packet1.getHeader();
    HanshakeAuthData authData1 = header1.getAuthData();
    assertThat(authData1.getBytes()).isEqualTo(authData.getBytes());
    assertThat(authData1.isEqual(authData)).isTrue();
    assertThat(header1.getStaticHeader().isEqual(header.getStaticHeader())).isTrue();
  }

  @Test
  void v4() {
    // Ping handshake message packet (flag 2, with ENR):
    //
    // # src-node-id = 0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb
    // # dest-node-id = 0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9
    // # nonce = 0xffffffffffffffffffffffff
    // # read-key = 0x4917330b5aeb51650213f90d5f253c45
    // # ping.req-id = 0x00000001
    // # ping.enr-seq = 1
    // #
    // # handshake inputs:
    // #
    // # whoareyou.request-nonce = 0x0102030405060708090a0b0c
    // # whoareyou.id-nonce = 0x0102030405060708090a0b0c0d0e0f1000000000000000000000000000000000
    // # whoareyou.enr-seq = 0
    // # ephemeral-key = 0x0288ef00023598499cb6c940146d050d2b1fb914198c327f76aad590bead68b6
    // # ephemeral-pubkey = 0x039a003ba6517b473fa0cd74aefe99dadfdb34627f90fec6362df85803908f53a5
    //
    // 00000000000000000000000000000000088b3d4342776668980a4adf72a8fcaa
    // 963f24b27a2f6bb44c7ed5ca10e87de130f94d2390b9853c3dcaa0d51e9472d4
    // 3c9ae48d04689ef4d3d2602a5e89ac340f9e81e722b1d7dac2578d520dd5bc6d
    // c1e38ad3ab33012be1a5d259267a0947bf242219834c5702d1c694c0ceb4a6a2
    // 7b5d68bd2c2e32e6cb9696706adff216ab862a9186875f9494150c4ae06fa4d1
    // f0396c93f215fa4ef52417d9c40a31564e8d5f31a7f08c38045ff5e30d966183
    // 8b1eabee9f1e561120bcc4d9f2f9c839152b4ab970e029b2395b97e8c3aa8d3b
    // 497ee98a15e865bcd34effa8b83eb6396bca60ad8f0bff1e047e278454bc2b3d
    // 6404c12106a9d0b6107fc2383976fc05fbda2c954d402c28c8fb53a2b3a4b111
    // c286ba2ac4ff880168323c6e97b01dbcbeef4f234e5849f75ab007217c919820
    // aaa1c8a7926d3625917fccc3d4569a69fd8aca026be87afab8e8e645d1ee8889
    // 92
    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(1));
    NodeRecord nodeRecord =
        NodeRecordFactory.DEFAULT.createFromValues(
            UInt64.valueOf(1),
            new EnrField(EnrField.ID, IdentitySchema.V4),
            new EnrField(EnrField.IP_V4, Bytes.fromHexString("0x7F000001")),
            new EnrField(
                EnrField.PKEY_SECP256K1,
                Bytes.fromHexString(
                    "0x0313D14211E0287B2361A1615890A9B5212080546D0A257AE4CFF96CF534992CB9")));
    nodeRecord.sign(
        Bytes.fromHexString("0xeef77acb6c6a6eebc5b363a475ac583ec7eccdb42b6481424c60f59aa326547f"));

    HanshakeAuthData authData =
        HanshakeAuthData.create(
            Bytes12.fromHexString("0xFFFFFFFFFFFFFFFFFFFFFFFF"),
            Bytes.fromHexString(
                "0xC14A44C1E56C122877E65606AD2CE92D1AD6E13E946D4CE0673B90E237BDD05C2181FC714C008686A08EB4DF52FAAB7614A469576E9AB1363377A7DE100AEDC2"),
            Bytes.fromHexString(
                "0x9A003BA6517B473FA0CD74AEFE99DADFDB34627F90FEC6362DF85803908F53A50F497889E4A9C74F48321875F8601EC65650FA0922FDA04D69089B79AF7F5533"),
            Optional.of(nodeRecord));
    Header<HanshakeAuthData> header =
        Header.create(
            Bytes32.fromHexString(
                "0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb"),
            Flag.HANDSHAKE,
            authData);
    Bytes key = Bytes.fromHexString("0x4917330b5aeb51650213f90d5f253c45");
    HandshakeMessagePacket packet = HandshakeMessagePacket.create(header, pingMessage, key);
    Bytes16 destNodeId =
        Bytes16.wrap(
            Bytes.fromHexString(
                "0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9"),
            0);
    RawPacket rawPacket =
        RawPacket.create(
            Bytes16.fromHexString("0x00000000000000000000000000000000"), packet, destNodeId);

    Bytes rawPacketBytes = rawPacket.getBytes();

    Bytes expectedBytes =
        Bytes.fromHexString(
            "00000000000000000000000000000000088b3d4342776668980a4adf72a8fcaa"
                + "963f24b27a2f6bb44c7ed5ca10e87de130f94d2390b9853c3dcaa0d51e9472d4"
                + "3c9ae48d04689ef4d3d2602a5e89ac340f9e81e722b1d7dac2578d520dd5bc6d"
                + "c1e38ad3ab33012be1a5d259267a0947bf242219834c5702d1c694c0ceb4a6a2"
                + "7b5d68bd2c2e32e6cb9696706adff216ab862a9186875f9494150c4ae06fa4d1"
                + "f0396c93f215fa4ef52417d9c40a31564e8d5f31a7f08c38045ff5e30d966183"
                + "8b1eabee9f1e561120bcc4d9f2f9c839152b4ab970e029b2395b97e8c3aa8d3b"
                + "497ee98a15e865bcd34effa8b83eb6396bca60ad8f0bff1e047e278454bc2b3d"
                + "6404c12106a9d0b6107fc2383976fc05fbda2c954d402c28c8fb53a2b3a4b111"
                + "c286ba2ac4ff880168323c6e97b01dbcbeef4f234e5849f75ab007217c919820"
                + "aaa1c8a7926d3625917fccc3d4569a69fd8aca026be87afab8e8e645d1ee8889"
                + "92");
    assertThat(rawPacketBytes).isEqualTo(expectedBytes);

    RawPacket rawPacket1 = RawPacket.decode(expectedBytes);
    HandshakeMessagePacket packet1 = (HandshakeMessagePacket) rawPacket1.decodePacket(destNodeId);
    assertThat(packet1.getMessageBytes()).isEqualTo(packet.getMessageBytes());
    PingMessage pingMessage1 = (PingMessage) packet1.decryptMessage(key, NodeRecordFactory.DEFAULT);
    assertThat(pingMessage1).isEqualTo(pingMessage);

    Header<HanshakeAuthData> header1 = packet1.getHeader();
    HanshakeAuthData authData1 = header1.getAuthData();
    assertThat(authData1.getBytes()).isEqualTo(authData.getBytes());
    assertThat(authData1.isEqual(authData)).isTrue();
    assertThat(header1.getStaticHeader().isEqual(header.getStaticHeader())).isTrue();

    Optional<NodeRecord> nodeRecord1 =
        packet1.getHeader().getAuthData().getNodeRecord(NodeRecordFactory.DEFAULT);
    assertThat(nodeRecord1).contains(nodeRecord);
  }
}
