/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import static org.ethereum.beacon.discovery.util.Functions.PUBKEY_SIZE;

import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.util.Utils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;

/**
 * Secondary tests not directly related to discovery but clarifying functions used somewhere in
 * discovery routines
 */
public class SubTests {
  /**
   * Tests BigInteger to byte[]. Take a look at {@link
   * Utils#extractBytesFromUnsignedBigInt(BigInteger, int)} for understanding the issue.
   */
  @Test
  public void testPubKeyBadPrefix() {
    Bytes privKey =
        Bytes.fromHexString("0xade78b68f25611ea57532f86bf01da909cc463465ed9efce9395403ff7fc99b5");
    ECKeyPair badKey = ECKeyPair.create(privKey.toArray());
    byte[] pubKey = Utils.extractBytesFromUnsignedBigInt(badKey.getPublicKey(), PUBKEY_SIZE);
    Assertions.assertEquals(64, pubKey.length);
  }
}
