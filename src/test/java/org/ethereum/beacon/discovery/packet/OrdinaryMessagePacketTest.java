/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.message.FindNodeMessage;
import org.ethereum.beacon.discovery.message.MessageCode;
import org.ethereum.beacon.discovery.message.NodesMessage;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.PongMessage;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.impl.MessagePacketImpl;
import org.ethereum.beacon.discovery.packet.impl.OrdinaryMessageImpl;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.util.DecryptException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;

public class OrdinaryMessagePacketTest {

  private final Bytes32 srcNodeId =
      Bytes32.fromHexString("0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb");
  private final Bytes32 destNodeId =
      Bytes32.fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
  private final Bytes16 headerMaskingKey = Bytes16.wrap(destNodeId, 0);
  private final Bytes12 aesGcmNonce = Bytes12.fromHexString("0xffffffffffffffffffffffff");
  private final Bytes secretKey = Bytes.fromHexString("0x00000000000000000000000000000000");
  private final Bytes16 aesCtrIV = Bytes16.fromHexString("0x00000000000000000000000000000000");

  static Stream<Arguments> testMessages() {
    return Stream.of(
        Arguments.of(
            new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(2)),
            "0x00000000000000000000000000000000088B3D4342776668980A4ADF72A8FCAA963F24B27A2F6BB44C7ED5CA10E87DE130F94D2390B9853C3FCBA22B1E9472D43C9AE48D04689EB84102ED931F66D180CBB4219F369A24F4E6B24D7BDC2A04",
            "Ping-1"),
        Arguments.of(
            new PingMessage(Bytes.fromHexString("0xFF0000000000000001"), UInt64.MAX_VALUE),
            "0x00000000000000000000000000000000088B3D4342776668980A4ADF72A8FCAA963F24B27A2F6BB44C7ED5CA10E87DE130F94D2390B9853C3FCBA22B1E9472D43C9AE48D04689EB8540F12931F67D3F47392BF576D35A62C2FB47B4BD52E04B98583B23D6E06E4BB274FA84E",
            "Ping-2"),
        Arguments.of(
            new PongMessage(
                Bytes.fromHexString("0x00000001"),
                UInt64.valueOf(2),
                Bytes.fromHexString("0x01020304"),
                777),
            "0x00000000000000000000000000000000088B3D4342776668980A4ADF72A8FCAA963F24B27A2F6BB44C7ED5CA10E87DE130F94D2390B9853C3FCBA22B1E9472D43C9AE48D04689EBB4902ED931F66D1707290BDDB10C950C10A8487FEDE689A827F73B62B91BA52",
            "Pong-1"),
        Arguments.of(
            new PongMessage(
                Bytes.fromHexString("0xFF0000000000000001"),
                UInt64.MAX_VALUE,
                Bytes.fromHexString("0xFFFFFFFF"),
                65535),
            "0x00000000000000000000000000000000088B3D4342776668980A4ADF72A8FCAA963F24B27A2F6BB44C7ED5CA10E87DE130F94D2390B9853C3FCBA22B1E9472D43C9AE48D04689EBB5C0F12931F67D3F47392BF576D35A62C2FB47B4BC1E3CD4EBD17FA8FE1A0EBDBF05909E4636057237F6464D4",
            "Pong-2"),
        Arguments.of(
            new FindNodeMessage(Bytes.fromHexString("0x00000001"), emptyList()),
            "0x00000000000000000000000000000000088B3D4342776668980A4ADF72A8FCAA963F24B27A2F6BB44C7ED5CA10E87DE130F94D2390B9853C3FCBA22B1E9472D43C9AE48D04689EBA4102ED931F6613CFA6CAD75DA6BDE3CFCCF239D14EF9D1",
            "FindNode-1"),
        Arguments.of(
            new FindNodeMessage(Bytes.fromHexString("0x00000001"), List.of(255, 254, 253)),
            "0x00000000000000000000000000000000088B3D4342776668980A4ADF72A8FCAA963F24B27A2F6BB44C7ED5CA10E87DE130F94D2390B9853C3FCBA22B1E9472D43C9AE48D04689EBA4B02ED931F6615758C13405E6F172DDCCA3A4A63F9A9C1C688D8C9886E",
            "FindNode-2"),
        Arguments.of(
            new NodesMessage(Bytes.fromHexString("0x00000001"), 0, emptyList()),
            "0x00000000000000000000000000000000088B3D4342776668980A4ADF72A8FCAA963F24B27A2F6BB44C7ED5CA10E87DE130F94D2390B9853C3FCBA22B1E9472D43C9AE48D04689EBD4002ED931F6653343E01E34DAB73355B39C13FB4ED0FC493",
            "Nodes-1"),
        Arguments.of(
            new NodesMessage(
                Bytes.fromHexString("0x00000001"),
                10000000,
                List.of(
                    NodeRecord.fromValues(
                        IdentitySchemaInterpreter.V4,
                        UInt64.MAX_VALUE,
                        List.of(
                            new EnrField("aaaaa1", Bytes.fromHexString("0xba0bab")),
                            new EnrField("aaaaa2", Bytes.fromHexString("0xb100da")))))),
            "0x00000000000000000000000000000000088B3D4342776668980A4ADF72A8FCAA963F24B27A2F6BB44C7ED5CA10E87DE130F94D2390B9853C3FCBA22B1E9472D43C9AE48D04689EBD7F0869931F67D277EB043E271132D86BB04B84B4451C32B1429505701562CD65A166957417635DAD967A60681C07CF01B9EEEBB9E351F468996784D03ED88D3C38D63AB4E42C40A0560310D95EC44A6BA4E3F68C25C7DA984B1592151FABF2968A8D97455177F79AFE41CFE65BC81B26BE8520FE4463448532E333B764AC94CE0B542B591FF893C51594F6525333C1027B217A22C90446EB76E47ACF058B2788",
            "Nodes-2"));
  }

  @ParameterizedTest(name = "{index} {2}")
  @MethodSource("testMessages")
  void testMessageRoundtrip(V5Message message, String expectedBytesHex, String name) {
    RawPacket rawPacket = createPacket(message);
    Bytes packetBytes = rawPacket.getBytes();
    Bytes expectedPacketBytes = Bytes.fromHexString(expectedBytesHex);
    assertThat(packetBytes).isEqualTo(expectedPacketBytes);

    RawPacket rawPacket1 = RawPacket.decode(packetBytes);
    OrdinaryMessagePacket packet =
        (OrdinaryMessagePacket) rawPacket1.decodePacket(headerMaskingKey);
    V5Message message1 = packet.decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
    assertThat(message1).isEqualTo(message);
  }

  @ParameterizedTest(name = "{index} {2}")
  @MethodSource("testMessages")
  void testDecryptingWithWrongKeyFails(V5Message message, String __, String name) {
    Bytes packetBytes = createPacket(message).getBytes();
    MessagePacket<?> msgPacket =
        (MessagePacket<?>) RawPacket.decode(packetBytes).decodePacket(headerMaskingKey);
    Bytes wrongSecretKey = Bytes.fromHexString("0x00000000000000000000000000000001");
    assertThatThrownBy(
            () -> {
              msgPacket.decryptMessage(wrongSecretKey, NodeRecordFactory.DEFAULT);
            })
        .isInstanceOf(DecryptException.class);
  }

  @ParameterizedTest(name = "{index} {2}")
  @MethodSource("testMessages")
  void testDecryptingWithWrongGcmNonceFails(V5Message message, String __, String name) {
    Header<AuthData> header = AuthData.createOrdinaryHeader(srcNodeId, aesGcmNonce);
    Bytes12 wrongAesGcmNonce = Bytes12.fromHexString("0xafffffffffffffffffffffff");
    Bytes encryptedMessage =
        MessagePacketImpl.encrypt(
            header.getBytes(), message.getBytes(), wrongAesGcmNonce, secretKey);

    OrdinaryMessagePacket messagePacket = new OrdinaryMessageImpl(header, encryptedMessage);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, messagePacket, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    MessagePacket<?> msgPacket =
        (MessagePacket<?>) RawPacket.decode(packetBytes).decodePacket(headerMaskingKey);
    assertThatThrownBy(
            () -> {
              msgPacket.decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
            })
        .isInstanceOf(DecryptException.class);
  }

  @Test
  void testDecryptingWithInvalidMessageCodeFails() {
    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(2));
    Header<AuthData> header = AuthData.createOrdinaryHeader(srcNodeId, aesGcmNonce);
    Bytes invalidMessageBytes = Bytes.wrap(Bytes.of(111), pingMessage.getBytes().slice(1));
    Bytes encryptedInvalidMessage =
        MessagePacketImpl.encrypt(header.getBytes(), invalidMessageBytes, aesGcmNonce, secretKey);

    OrdinaryMessagePacket messagePacket = new OrdinaryMessageImpl(header, encryptedInvalidMessage);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, messagePacket, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    MessagePacket<?> msgPacket =
        (MessagePacket<?>) RawPacket.decode(packetBytes).decodePacket(headerMaskingKey);
    assertThatThrownBy(
            () -> {
              msgPacket.decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
            })
        .isInstanceOf(DecodeException.class);
  }

  @Test
  void testDecryptingRandomMessageFails() {
    Header<AuthData> header = AuthData.createOrdinaryHeader(srcNodeId, aesGcmNonce);

    OrdinaryMessagePacket messagePacket = OrdinaryMessagePacket.createRandom(header, 111);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, messagePacket, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    MessagePacket<?> msgPacket =
        (MessagePacket<?>) RawPacket.decode(packetBytes).decodePacket(headerMaskingKey);
    assertThatThrownBy(
            () -> {
              msgPacket.decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
            })
        .isInstanceOf(DecodeException.class);
  }

  @ParameterizedTest(name = "{index} {2}")
  @MethodSource("testMessages")
  void testDecryptingWithInvalidMessageRlpFails(V5Message message, String __, String name) {
    Header<AuthData> header = AuthData.createOrdinaryHeader(srcNodeId, aesGcmNonce);
    Bytes messageBytes = message.getBytes();
    Bytes invalidMessageBytes = messageBytes.slice(0, messageBytes.size() - 1);
    Bytes encryptedInvalidMessage =
        MessagePacketImpl.encrypt(header.getBytes(), invalidMessageBytes, aesGcmNonce, secretKey);

    OrdinaryMessagePacket messagePacket = new OrdinaryMessageImpl(header, encryptedInvalidMessage);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, messagePacket, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    MessagePacket<?> msgPacket =
        (MessagePacket<?>) RawPacket.decode(packetBytes).decodePacket(headerMaskingKey);
    assertThatThrownBy(
            () -> {
              msgPacket.decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
            })
        .isInstanceOf(DecodeException.class);
  }

  @Test
  void testDecryptingWithInvalidMessageRequestIdRlpFails() {
    Header<AuthData> header = AuthData.createOrdinaryHeader(srcNodeId, aesGcmNonce);
    RlpList rlpList =
        new RlpList(
            new RlpList(), // should be RlpString with requestId
            RlpString.create(1));
    Bytes invalidMessageBytes =
        Bytes.concatenate(
            Bytes.of(MessageCode.PING.byteCode()), Bytes.wrap(RlpEncoder.encode(rlpList)));

    Bytes encryptedInvalidMessage =
        MessagePacketImpl.encrypt(header.getBytes(), invalidMessageBytes, aesGcmNonce, secretKey);

    OrdinaryMessagePacket messagePacket = new OrdinaryMessageImpl(header, encryptedInvalidMessage);
    RawPacket rawPacket = RawPacket.create(aesCtrIV, messagePacket, headerMaskingKey);
    Bytes packetBytes = rawPacket.getBytes();

    MessagePacket<?> msgPacket =
        (MessagePacket<?>) RawPacket.decode(packetBytes).decodePacket(headerMaskingKey);
    assertThatThrownBy(
            () -> {
              msgPacket.decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
            })
        .isInstanceOf(DecodeException.class);
  }

  private RawPacket createPacket(V5Message msg) {
    Header<AuthData> header = AuthData.createOrdinaryHeader(srcNodeId, aesGcmNonce);
    OrdinaryMessagePacket messagePacket = OrdinaryMessagePacket.create(header, msg, secretKey);
    return RawPacket.create(aesCtrIV, messagePacket, headerMaskingKey);
  }
}
