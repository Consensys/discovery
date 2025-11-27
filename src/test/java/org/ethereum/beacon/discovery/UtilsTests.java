/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import static org.ethereum.beacon.discovery.util.Functions.PUBKEY_SIZE;

import java.math.BigInteger;
import org.apache.tuweni.v2.bytes.Bytes;
import org.apache.tuweni.v2.bytes.Bytes32;
import org.apache.tuweni.v2.crypto.SECP256K1.KeyPair;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UtilsTests {
  /**
   * Tests BigInteger to byte[]. Take a look at {@link
   * Utils#extractBytesFromUnsignedBigInt(BigInteger, int)} for understanding the issue.
   */
  @Test
  public void testPubKeyBadPrefix() {
    Bytes privKey =
        Bytes32.fromHexString("0xade78b68f25611ea57532f86bf01da909cc463465ed9efce9395403ff7fc99b5");
    KeyPair badKey = Functions.createKeyPairFromSecretBytes(privKey);
    // Straightforward way to notice the issue
    byte[] badPubKey = badKey.publicKey().bytes().toUnsignedBigInteger().toByteArray();
    byte[] goodPubKey =
        Utils.extractBytesFromUnsignedBigInt(
            badKey.publicKey().bytes().toUnsignedBigInteger(), PUBKEY_SIZE);
    Assertions.assertEquals(65, badPubKey.length);
    Assertions.assertEquals(64, goodPubKey.length);
  }
}
