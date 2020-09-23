package org.ethereum.beacon.discovery.packet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.message.PingMessage;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.packet.StaticHeader.Flag;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.util.DecryptException;
import org.junit.jupiter.api.Test;

public class OrdinaryMessagePacketTest {

  private Bytes32 srcNodeId = Bytes32.fromHexString(
      "0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb");
  private Bytes32 destNodeId = Bytes32.fromHexString(
      "0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
  private Bytes16 headerMaskingKey = Bytes16.wrap(destNodeId,0);
  private Bytes12 aesGcmNonce = Bytes12.fromHexString("0xffffffffffffffffffffffff");
  private Bytes secretKey = Bytes.fromHexString("0x00000000000000000000000000000000");
  private Bytes16 aesCtrIV = Bytes16.fromHexString("0x00000000000000000000000000000000");

  @Test
  void pingRoundtrip() {
    PingMessage pingMessage = new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(2));
    RawPacket rawPacket = createPacket(pingMessage);
    Bytes packetBytes = rawPacket.getBytes();
    Bytes expectedPacketBytes = Bytes.fromHexString(
        "0x00000000000000000000000000000000088B3D4342776668980A4ADF72A8FCAA963F24B27A2F6BB44C7ED5CA10E87DE130F94D2390B9853C3FCBA22B1E9472D43C9AE48D04689EB84102ED931F66D180CBB4219F369A24F4E6B24D7BDC2A04");
    assertThat(packetBytes).isEqualTo(expectedPacketBytes);

    RawPacket rawPacket1 = RawPacket.decode(packetBytes);
    OrdinaryMessagePacket packet = (OrdinaryMessagePacket) rawPacket1.decodePacket(headerMaskingKey);
    PingMessage pingMessage1 = (PingMessage) packet
        .decryptMessage(secretKey, NodeRecordFactory.DEFAULT);
    assertThat(pingMessage1).isEqualTo(pingMessage);
  }

  @Test
  void testDecryptingWithStaleKeyFails() {
    Bytes packetBytes = createPacket(
        new PingMessage(Bytes.fromHexString("0x00000001"), UInt64.valueOf(2))).getBytes();
    MessagePacket<?> msgPacket = (MessagePacket<?>) RawPacket.decode(packetBytes)
        .decodePacket(headerMaskingKey);
    Bytes wrongSecretKey = Bytes.fromHexString("0x00000000000000000000000000000001");
    assertThatThrownBy(() -> {
      msgPacket.decryptMessage(wrongSecretKey, NodeRecordFactory.DEFAULT);
    }).isInstanceOf(DecryptException.class);
  }

  private RawPacket createPacket(V5Message msg) {
    AuthData authData = AuthData.create(aesGcmNonce);
    Header<AuthData> header = Header.create(srcNodeId, Flag.MESSAGE, authData);
    OrdinaryMessagePacket messagePacket = OrdinaryMessagePacket.create(header, msg, secretKey);
    return RawPacket.create(aesCtrIV, messagePacket, headerMaskingKey);
  }
}
