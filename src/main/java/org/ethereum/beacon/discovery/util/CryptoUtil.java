/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class CryptoUtil {

  private static final BouncyCastleProvider securityProvider = new BouncyCastleProvider();

  public static Bytes sha256(final Bytes indexBytes) {
    final MessageDigest sha256Digest = getSha256Digest();
    indexBytes.update(sha256Digest);
    return Bytes.wrap(sha256Digest.digest());
  }

  private static MessageDigest getSha256Digest() {
    try {
      return MessageDigest.getInstance("sha256", securityProvider);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
