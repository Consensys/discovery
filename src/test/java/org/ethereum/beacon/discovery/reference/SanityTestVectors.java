/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ethereum.beacon.discovery.TestUtil.isFieldsEqual;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HandshakeAuthData;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket.OrdinaryAuthData;
import org.ethereum.beacon.discovery.packet.Packet;
import org.ethereum.beacon.discovery.packet.RawPacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SanityTestVectors {

  Bytes nodeAKey =
      Bytes.fromHexString("0xeef77acb6c6a6eebc5b363a475ac583ec7eccdb42b6481424c60f59aa326547f");
  Bytes nodeBKey =
      Bytes.fromHexString("0x66fb62bfbd66b9177a138c1e5cddbe4f7c30c343e94e68df8769459cb1cde628");

  @Test
  void testOrdinaryPingPacket() {
    Bytes32 srcNodeId =
        Bytes32.fromHexString("0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb");
    Bytes32 destNodeId =
        Bytes32.fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
    Bytes12 nonce = Bytes12.fromHexString("0xffffffffffffffffffffffff");
    Bytes16 readKey = Bytes16.fromHexString("0x00000000000000000000000000000000");
    Bytes16 maskingIV = Bytes16.fromHexString("0x00000000000000000000000000000000");
    Bytes pingReqId = Bytes.fromHexString("0x00000001");
    UInt64 pingEnrSeq = UInt64.valueOf(2);

    Bytes expectedPacketBytes =
        Bytes.fromHexString(
            "00000000000000000000000000000000088b3d4342774649325f313964a39e55"
                + "ea96c005ad52be8c7560413a7008f16c9e6d2f43bbea8814a546b7409ce783d3"
                + "4c4f53245d08dab84102ed931f66d1492acb308fa1c6715b9d139b81acbdcc");

    PingMessage pingMessage = new PingMessage(pingReqId, pingEnrSeq);
    Header<OrdinaryAuthData> header = Header.createOrdinaryHeader(srcNodeId, nonce);
    OrdinaryMessagePacket packet =
        OrdinaryMessagePacket.create(maskingIV, header, pingMessage, readKey);
    Bytes16 maskingKey = Bytes16.wrap(destNodeId, 0);
    RawPacket rawPacket = RawPacket.createAndMask(maskingIV, packet, maskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    assertThat(packetBytes).isEqualTo(expectedPacketBytes);

    RawPacket packet1 = RawPacket.decode(packetBytes);
    Packet<?> packet2 = packet1.demaskPacket(maskingKey);
    OrdinaryMessagePacket messagePacket = (OrdinaryMessagePacket) packet2;
    V5Message v5Message =
        messagePacket.decryptMessage(maskingIV, readKey, NodeRecordFactory.DEFAULT);

    Assertions.assertEquals(pingMessage, v5Message);
  }

  @Test
  void testWhoAreYouPacket() {
    Bytes32 destNodeId =
        Bytes32.fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
    Bytes16 maskingIV = Bytes16.fromHexString("0x00000000000000000000000000000000");
    Bytes12 whoareyouRequestNonce = Bytes12.fromHexString("0x0102030405060708090a0b0c");
    Bytes16 whoareyouIdNonce = Bytes16.fromHexString("0x0102030405060708090a0b0c0d0e0f10");
    UInt64 whoareyouEnrSeq = UInt64.valueOf(0);

    Header<WhoAreYouAuthData> header =
        Header.createWhoAreYouHeader(whoareyouRequestNonce, whoareyouIdNonce, whoareyouEnrSeq);
    WhoAreYouPacket packet = WhoAreYouPacket.create(header);
    Bytes16 maskingKey = Bytes16.wrap(destNodeId, 0);
    RawPacket rawPacket = RawPacket.createAndMask(maskingIV, packet, maskingKey);
    Bytes rawPacketBytes = rawPacket.getBytes();

    Bytes expectedBytes =
        Bytes.fromHexString(
            "00000000000000000000000000000000088b3d434277464933a1ccc59f5967ad"
                + "1d6035f15e528627dde75cd68292f9e6c27d6b66c8100a873fcbaed4e16b8d");

    assertThat(rawPacketBytes).isEqualTo(expectedBytes);

    RawPacket rawPacket1 = RawPacket.decode(rawPacketBytes);
    assertThat(rawPacket1.getMaskingIV()).isEqualTo(rawPacket.getMaskingIV());

    WhoAreYouPacket packet1 =
        (WhoAreYouPacket) rawPacket1.demaskPacket(Bytes16.wrap(destNodeId, 0));
    assertThat(packet1.getMessageCyphered().isEmpty()).isTrue();
    Header<WhoAreYouAuthData> header1 = packet1.getHeader();
    WhoAreYouAuthData authData1 = header1.getAuthData();
    assertThat(authData1.getBytes()).isEqualTo(header.getAuthData().getBytes());
    assertThat(isFieldsEqual(authData1, header.getAuthData())).isTrue();
    assertThat(isFieldsEqual(header1.getStaticHeader(), header.getStaticHeader())).isTrue();
  }

  @Test
  void testHandshakePacket() {

    Bytes32 srcNodeId =
        Bytes32.fromHexString("0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb");
    Bytes32 destNodeId =
        Bytes32.fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
    Bytes16 maskingIV = Bytes16.fromHexString("0x00000000000000000000000000000000");
    Bytes12 nonce = Bytes12.fromHexString("0xffffffffffffffffffffffff");
    Bytes16 readKey = Bytes16.fromHexString("0x4f9fac6de7567d1e3b1241dffe90f662");
    Bytes pingReqId = Bytes.fromHexString("0x00000001");
    UInt64 pingEnrSeq = UInt64.valueOf(1);

    // handshake inputs:
    Bytes whoareyouChallengeData =
        Bytes.fromHexString(
            "0x000000000000000000000000000000006469736376350001010102030405060708090a0b0c00180102030405060708090a0b0c0d0e0f100000000000000001");
    Bytes ephemeralPubkey =
        Bytes.fromHexString("0x039a003ba6517b473fa0cd74aefe99dadfdb34627f90fec6362df85803908f53a5");

    Bytes expectedPacketBytes =
        Bytes.fromHexString(
            "00000000000000000000000000000000088b3d4342774649305f313964a39e55"
                + "ea96c005ad521d8c7560413a7008f16c9e6d2f43bbea8814a546b7409ce783d3"
                + "4c4f53245d08da4bb252012b2cba3f4f374a90a75cff91f142fa9be3e0a5f3ef"
                + "268ccb9065aeecfd67a999e7fdc137e062b2ec4a0eb92947f0d9a74bfbf44dfb"
                + "a776b21301f8b65efd5796706adff216ab862a9186875f9494150c4ae06fa4d1"
                + "f0396c93f215fa4ef524f1eadf5f0f4126b79336671cbcf7a885b1f8bd2a5d83"
                + "9cf8");

    PingMessage pingMessage = new PingMessage(pingReqId, pingEnrSeq);
    Bytes idSignature =
        HandshakeAuthData.signId(whoareyouChallengeData, ephemeralPubkey, destNodeId, nodeAKey);

    Header<HandshakeAuthData> header =
        Header.createHandshakeHeader(
            srcNodeId, nonce, idSignature, ephemeralPubkey, Optional.empty());
    HandshakeMessagePacket packet =
        HandshakeMessagePacket.create(maskingIV, header, pingMessage, readKey);
    Bytes16 maskingKey = Bytes16.wrap(destNodeId, 0);
    RawPacket rawPacket = RawPacket.createAndMask(maskingIV, packet, maskingKey);

    Bytes rawPacketBytes = rawPacket.getBytes();

    assertThat(rawPacketBytes).isEqualTo(expectedPacketBytes);

    RawPacket rawPacket1 = RawPacket.decode(rawPacketBytes);
    HandshakeMessagePacket packet1 = (HandshakeMessagePacket) rawPacket1.demaskPacket(destNodeId);
    assertThat(packet1.getMessageCyphered()).isEqualTo(packet.getMessageCyphered());
    PingMessage pingMessage1 =
        (PingMessage) packet1.decryptMessage(maskingIV, readKey, NodeRecordFactory.DEFAULT);
    assertThat(pingMessage1).isEqualTo(pingMessage);

    Header<HandshakeAuthData> header1 = packet1.getHeader();
    HandshakeAuthData authData1 = header1.getAuthData();
    assertThat(authData1.getBytes()).isEqualTo(header.getAuthData().getBytes());
    assertThat(isFieldsEqual(authData1, header.getAuthData(), NodeRecordFactory.DEFAULT)).isTrue();
    assertThat(isFieldsEqual(header1.getStaticHeader(), header.getStaticHeader())).isTrue();
  }

  @Test
  void testHandshakeWithEnrPacket() {

    Bytes32 srcNodeId =
        Bytes32.fromHexString("0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb");
    Bytes32 destNodeId =
        Bytes32.fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
    Bytes16 maskingIV = Bytes16.fromHexString("0x00000000000000000000000000000000");
    Bytes12 nonce = Bytes12.fromHexString("0xffffffffffffffffffffffff");
    Bytes16 readKey = Bytes16.fromHexString("0x53b1c075f41876423154e157470c2f48");
    Bytes pingReqId = Bytes.fromHexString("0x00000001");
    UInt64 pingEnrSeq = UInt64.valueOf(1);

    NodeRecord nodeRecord =
        NodeRecord.fromValues(
            IdentitySchemaInterpreter.V4,
            UInt64.valueOf(1),
            List.of(
                new EnrField(
                    EnrField.PKEY_SECP256K1,
                    Bytes.fromHexString(
                        "0x0313D14211E0287B2361A1615890A9B5212080546D0A257AE4CFF96CF534992CB9")),
                new EnrField(EnrField.ID, IdentitySchema.V4),
                new EnrField(EnrField.IP_V4, Bytes.fromHexString("0x7F000001"))));
    nodeRecord.setSignature(
        Bytes.fromHexString(
            "0x17E1B073918DA32D640642C762C0E2781698E4971F8AB39A77746ADAD83F01E76FFC874C5924808BBE7C50890882C2B8A01287A0B08312D1D53A17D517F5EB27"));

    // handshake inputs:
    Bytes whoareyouChallengeData =
        Bytes.fromHexString(
            "0x000000000000000000000000000000006469736376350001010102030405060708090a0b0c00180102030405060708090a0b0c0d0e0f100000000000000000");
    Bytes ephemeralPubkey =
        Bytes.fromHexString("0x039a003ba6517b473fa0cd74aefe99dadfdb34627f90fec6362df85803908f53a5");

    Bytes expectedPacketBytes =
        Bytes.fromHexString(
            "00000000000000000000000000000000088b3d4342774649305f313964a39e55"
                + "ea96c005ad539c8c7560413a7008f16c9e6d2f43bbea8814a546b7409ce783d3"
                + "4c4f53245d08da4bb23698868350aaad22e3ab8dd034f548a1c43cd246be9856"
                + "2fafa0a1fa86d8e7a3b95ae78cc2b988ded6a5b59eb83ad58097252188b902b2"
                + "1481e30e5e285f19735796706adff216ab862a9186875f9494150c4ae06fa4d1"
                + "f0396c93f215fa4ef524e0ed04c3c21e39b1868e1ca8105e585ec17315e755e6"
                + "cfc4dd6cb7fd8e1a1f55e49b4b5eb024221482105346f3c82b15fdaae36a3bb1"
                + "2a494683b4a3c7f2ae41306252fed84785e2bbff3b022812d0882f06978df84a"
                + "80d443972213342d04b9048fc3b1d5fcb1df0f822152eced6da4d3f6df27e70e"
                + "4539717307a0208cd208d65093ccab5aa596a34d7511401987662d8cf62b1394"
                + "71");

    PingMessage pingMessage = new PingMessage(pingReqId, pingEnrSeq);
    Bytes idSignature =
        HandshakeAuthData.signId(whoareyouChallengeData, ephemeralPubkey, destNodeId, nodeAKey);

    Header<HandshakeAuthData> header =
        Header.createHandshakeHeader(
            srcNodeId, nonce, idSignature, ephemeralPubkey, Optional.of(nodeRecord));
    HandshakeMessagePacket packet =
        HandshakeMessagePacket.create(maskingIV, header, pingMessage, readKey);
    Bytes16 maskingKey = Bytes16.wrap(destNodeId, 0);
    RawPacket rawPacket = RawPacket.createAndMask(maskingIV, packet, maskingKey);

    Bytes rawPacketBytes = rawPacket.getBytes();

    assertThat(rawPacketBytes).isEqualTo(expectedPacketBytes);

    RawPacket rawPacket1 = RawPacket.decode(expectedPacketBytes);
    HandshakeMessagePacket packet1 = (HandshakeMessagePacket) rawPacket1.demaskPacket(destNodeId);
    assertThat(packet1.getMessageCyphered()).isEqualTo(packet.getMessageCyphered());
    PingMessage pingMessage1 =
        (PingMessage) packet1.decryptMessage(maskingIV, readKey, NodeRecordFactory.DEFAULT);
    assertThat(pingMessage1).isEqualTo(pingMessage);

    Header<HandshakeAuthData> header1 = packet1.getHeader();
    HandshakeAuthData authData1 = header1.getAuthData();
    Optional<NodeRecord> nodeRecord1 = authData1.getNodeRecord(NodeRecordFactory.DEFAULT);
    assertThat(authData1.getBytes()).isEqualTo(header.getAuthData().getBytes());
    assertThat(isFieldsEqual(authData1, header.getAuthData(), NodeRecordFactory.DEFAULT)).isTrue();
    assertThat(isFieldsEqual(header1.getStaticHeader(), header.getStaticHeader())).isTrue();
    assertThat(nodeRecord1).contains(nodeRecord);
  }
}
