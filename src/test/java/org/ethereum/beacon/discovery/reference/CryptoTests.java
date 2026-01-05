/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.reference;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.InMemorySecurityModule;
import org.ethereum.beacon.discovery.SecurityModule;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HandshakeAuthData;
import org.ethereum.beacon.discovery.util.CryptoUtil;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Functions.HKDFKeys;
import org.junit.jupiter.api.Test;

public class CryptoTests {
  private static final SecretKey LOCAL_SECRET =
      Functions.createSecretKey(
          Bytes32.fromHexString(
              "0xfb757dc581730490a1d7a00deea65e9b1936924caaea8f44d476014856b68736"));

  private final SecurityModule securityModule = InMemorySecurityModule.create(LOCAL_SECRET);

  @Test
  void testECDH() {
    Bytes publicKeyBytes =
        Bytes.fromHexString("0x039961e4c2356d61bedb83052c115d311acb3a96f5777296dcf297351130266231");
    Bytes expectedSharedSecretBytes =
        Bytes.fromHexString("0x033b11a2a1f214567e1537ce5e509ffd9b21373247f2a3ff6841f4976f53165e7e");

    Bytes keyAgreement = Functions.deriveECDHKeyAgreement(LOCAL_SECRET, publicKeyBytes);
    assertThat(keyAgreement).isEqualTo(expectedSharedSecretBytes);
  }

  @Test
  void testKeyDerivation() {
    Bytes destPubkeyBytes =
        Bytes.fromHexString("0x0317931e6e0840220642f230037d285d122bc59063221ef3226b1f403ddc69ca91");
    Bytes nodeIdABytes =
        Bytes.fromHexString("0xaaaa8419e9f49d0083561b48287df592939a8d19947d8c0ef88f2a4856a69fbb");
    Bytes nodeIdBBytes =
        Bytes.fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");
    Bytes challengeDataBytes =
        Bytes.fromHexString(
            "0x000000000000000000000000000000006469736376350001010102030405060708090a0b0c00180102030405060708090a0b0c0d0e0f100000000000000000");

    Bytes expectedInitiatorKeyBytes = Bytes.fromHexString("0xdccc82d81bd610f4f76d3ebe97a40571");
    Bytes expectedRecipientKeyBytes = Bytes.fromHexString("0xac74bb8773749920b0d3a8881c173ec5");

    HKDFKeys hkdfKeys =
        Functions.hkdfExpand(
            nodeIdABytes, nodeIdBBytes, LOCAL_SECRET, destPubkeyBytes, challengeDataBytes);
    assertThat(hkdfKeys.getInitiatorKey()).isEqualTo(expectedInitiatorKeyBytes);
    assertThat(hkdfKeys.getRecipientKey()).isEqualTo(expectedRecipientKeyBytes);
  }

  @Test
  void testChallengeSign() {
    Bytes challengeDataBytes =
        Bytes.fromHexString(
            "0x000000000000000000000000000000006469736376350001010102030405060708090a0b0c00180102030405060708090a0b0c0d0e0f100000000000000000");
    Bytes ephemeralPubkeyBytes =
        Bytes.fromHexString("0x039961e4c2356d61bedb83052c115d311acb3a96f5777296dcf297351130266231");
    Bytes32 nodeIdBBytes =
        Bytes32.fromHexString("0xbbbb9d047f0488c0b5a93c1c3f2d8bafc7c8ff337024a55434a0d0555de64db9");

    Bytes expectedSignatureBytes =
        Bytes.fromHexString(
            "0x94852a1e2318c4e5e9d422c98eaf19d1d90d876b29cd06ca7cb7546d0fff7b484fe86c09a064fe72bdbef73ba8e9c34df0cd2b53e9d65528c2c7f336d5dfc6e6");

    Bytes signatureBytes =
        HandshakeAuthData.signId(
            challengeDataBytes, ephemeralPubkeyBytes, nodeIdBBytes, securityModule);
    assertThat(signatureBytes).isEqualTo(expectedSignatureBytes);
  }

  @Test
  void testAesGcm() {
    Bytes encryptionKeyBytes = Bytes.fromHexString("0x9f2d77db7004bf8a1a85107ac686990b");
    Bytes nonceBytes = Bytes.fromHexString("0x27b5af763c446acd2749fe8e");
    Bytes ptBytes = Bytes.fromHexString("0x01c20101");
    Bytes adBytes =
        Bytes.fromHexString("0x93a7400fa0d6a694ebc24d5cf570f65d04215b6ac00757875e3f3a5f42107903");

    Bytes expectedMessageCiphertextBytes =
        Bytes.fromHexString("0xa5d12a2d94b8ccb3ba55558229867dc13bfa3648");

    Bytes messageCypherText =
        CryptoUtil.aesgcmEncrypt(encryptionKeyBytes, nonceBytes, ptBytes, adBytes);
    assertThat(messageCypherText).isEqualTo(expectedMessageCiphertextBytes);
  }
}
