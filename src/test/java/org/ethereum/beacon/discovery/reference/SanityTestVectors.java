package org.ethereum.beacon.discovery.reference;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet5_1.AuthData;
import org.ethereum.beacon.discovery.packet5_1.Header;
import org.ethereum.beacon.discovery.packet5_1.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet5_1.Packet;
import org.ethereum.beacon.discovery.packet5_1.RawPacket;
import org.ethereum.beacon.discovery.packet5_1.StaticHeader;
import org.ethereum.beacon.discovery.packet5_1.StaticHeader.Flag;
import org.ethereum.beacon.discovery.packet5_1.WhoAreYouPacket;
import org.ethereum.beacon.discovery.packet5_1.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.Functions;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SanityTestVectors {

  @Test
  void simpleEncodeDecode() {
    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x01"), UInt64.ZERO);
    AuthData authData = AuthData.create(Bytes12.fromHexString("0x050505050505050505050505"));
    StaticHeader staticHeader = StaticHeader.create(
        Bytes32.fromHexString("0x0303030303030303030303030303030303030303030303030303030303030303"),
        Flag.MESSAGE,
        authData.getBytes().size());
    Header<AuthData> header = Header.create(staticHeader, authData);
    Bytes key = Bytes.fromHexString("0x01010101010101010101010101010101");
    OrdinaryMessagePacket packet = OrdinaryMessagePacket.create(header, pingMessage, key);
    Bytes16 destPeerId = Bytes16.wrap(
        Bytes.fromHexString(
            "0x0404040404040404040404040404040404040404040404040404040404040404"), 0);
    RawPacket rawPacket = RawPacket.create(
        Bytes16.fromHexString("0x02020202020202020202020202020202"),
        packet,
        destPeerId);
    System.out.println(rawPacket.getBytes());

    RawPacket packet1 = RawPacket.decode(rawPacket.getBytes());
    Packet<?> packet2 = packet1.decodePacket(destPeerId);
    OrdinaryMessagePacket messagePacket = (OrdinaryMessagePacket) packet2;
    V5Message v5Message = messagePacket.decryptMessage(key, NodeRecordFactory.DEFAULT);

    Assertions.assertEquals(pingMessage, v5Message);
  }

  @Test
  void vector2() {
    Bytes32 nodeAPrivKey = Bytes32
        .fromHexString("0xeef77acb6c6a6eebc5b363a475ac583ec7eccdb42b6481424c60f59aa326547f");
    Bytes nodeAPubKey = Functions.derivePublicKeyFromPrivate(nodeAPrivKey);
    Bytes32 nodeBPrivKey = Bytes32
        .fromHexString("0x66fb62bfbd66b9177a138c1e5cddbe4f7c30c343e94e68df8769459cb1cde628");
    Bytes nodeBPubKey = Functions.derivePublicKeyFromPrivate(nodeBPrivKey);

    Bytes nodeASecretKey = Functions.deriveECDHKeyAgreement(nodeAPrivKey, nodeBPubKey);
    Bytes nodeBSecretKey = Functions.deriveECDHKeyAgreement(nodeBPrivKey, nodeAPubKey);

    Bytes32 srcNodeId = Bytes32
        .fromHexString("0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb");
    Bytes32 destNodeId = Bytes32
        .fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
    Bytes msg = Bytes.fromHexString(
        "00000000000000000000000000000000088b3d4342776668980a4adf72a8fcaa"
            + "963f24b27a2f6bb44c7ed5ca10e87de130f94d2390b9853c3fcba22b1e9472d4"
            + "3c9ae48d04689eb84102ed931f66d180cbb4219f369a24f4e6b24d7bdc2a04");
    RawPacket rawPacket = RawPacket.decode(msg);
    Packet<?> packet = rawPacket.decodePacket(Bytes16.wrap(destNodeId, 0));
    OrdinaryMessagePacket msgPacket = (OrdinaryMessagePacket) packet;

    V5Message v5Message = msgPacket.decryptMessage(
        Bytes.fromHexString("0x00000000000000000000000000000000"),
        NodeRecordFactory.DEFAULT);
    System.out.println(v5Message);
  }

  @Test
  void vector3() {
    Bytes16 nodeAKey = Bytes16
        .fromHexString("0x01010101010101010101010101010101");
    Bytes32 nodeBKey = Bytes32
        .fromHexString("0x66fb62bfbd66b9177a138c1e5cddbe4f7c30c343e94e68df8769459cb1cde628");

    Bytes32 srcNodeId = Bytes32
        .fromHexString("0x0303030303030303030303030303030303030303030303030303030303030303");
    Bytes32 destNodeId = Bytes32
        .fromHexString("0x0404040404040404040404040404040404040404040404040404040404040404");
    Bytes msg = Bytes.fromHexString(
        "0x0aa2702c8135c1276a1c00c81e04f6cec58e496f3d2dd3283f34cbdeb97364e15e201821954a5ad0f20a16d9385f1d56eb847b9d8b936c09661ee331db73fd4166474b1a962a42522611e34e608ad5f8b619eeef6ad820edf74d35");
    RawPacket rawPacket = RawPacket.decode(msg);
    Packet<?> packet = rawPacket.decodePacket(Bytes16.wrap(destNodeId, 0));
    OrdinaryMessagePacket msgPacket = (OrdinaryMessagePacket) packet;

    V5Message v5Message = msgPacket.decryptMessage(nodeAKey, NodeRecordFactory.DEFAULT);
    System.out.println(v5Message);
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

    WhoAreYouAuthData authData = WhoAreYouAuthData.create(
        Bytes12.fromHexString("0x0102030405060708090a0b0c"),
        Bytes32.fromHexString("0x0102030405060708090a0b0c0d0e0f1000000000000000000000000000000000"),
        UInt64.ZERO);
    Header<WhoAreYouAuthData> header = Header.create(
        Bytes32.fromHexString("0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb"),
        Flag.WHOAREYOU, authData);
    WhoAreYouPacket packet = WhoAreYouPacket.create(header);
    Bytes32 destNodeId = Bytes32
        .fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
    RawPacket rawPacket = RawPacket
        .create(Bytes16.fromHexString("00000000000000000000000000000000"), packet,
            Bytes16.wrap(destNodeId, 0));
    Bytes rawPacketBytes = rawPacket.getBytes();

    Bytes expectedBytes = Bytes.fromHexString(
        "00000000000000000000000000000000088b3d4342776668980a4adf72a8fcaa"
            + "963f24b27a2f6bb44c7ed5ca10e87de130f94d2390b9853c3ecb9ad5e368892e"
            + "c562137bf19c6d0a9191a5651c4f415117bdfa0c7ab86af62b7a9784eceb2800"
            + "8d03ede83bd1369631f9f3d8da0b45");

    assertThat(rawPacketBytes).isEqualTo(expectedBytes);

    RawPacket rawPacket1 = RawPacket.decode(rawPacketBytes);
    assertThat(rawPacket1.getIV()).isEqualTo(rawPacket.getIV());

    WhoAreYouPacket packet1 = (WhoAreYouPacket) rawPacket1
        .decodePacket(Bytes16.wrap(destNodeId, 0));
    assertThat(packet1.getMessageBytes().isEmpty()).isTrue();
    Header<WhoAreYouAuthData> header1 = packet1.getHeader();
    WhoAreYouAuthData authData1 = header1.getAuthData();
    assertThat(authData1.getBytes()).isEqualTo(authData.getBytes());
    assertThat(authData1.getRequestNonce()).isEqualTo(authData.getRequestNonce());
    assertThat(authData1.getEnrSeq()).isEqualTo(authData.getEnrSeq());
    assertThat(authData1.getIdNonce()).isEqualTo(authData.getIdNonce());

    assertThat(header1.getStaticHeader().isEqual(header.getStaticHeader())).isTrue();
  }
}
