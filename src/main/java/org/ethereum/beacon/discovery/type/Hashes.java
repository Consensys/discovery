/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.type;

import java.security.MessageDigest;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jcajce.provider.digest.SHA256.Digest;

/** Utility methods to calculate message hashes */
public abstract class Hashes {

  private Hashes() {}

  private static final String SHA256 = "SHA-256";

  /**
   * A low level method that calculates hash using give algorithm.
   *
   * @param input a message.
   * @param algorithm an algorithm.
   * @return the hash.
   */
  private static byte[] digestUsingAlgorithm(
      Bytes input, @SuppressWarnings("unused") String algorithm) {
    MessageDigest digest;
    try {
      // TODO integrate with JCA without performance loose
      //      digest = MessageDigest.getInstance(algorithm, "BC");
      digest = new Digest();
      input.update(digest);
      return digest.digest();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Calculates sha256 hash.
   *
   * @param input input message.
   * @return the hash.
   */
  public static Bytes sha256(Bytes input) {
    byte[] output = digestUsingAlgorithm(input, SHA256);
    return Bytes.wrap(Bytes.wrap(output));
  }
}
