/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.util;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
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

  public static Bytes aesctrEncrypt(Bytes key, Bytes iv, Bytes plain) {
    try {
      Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(key.toArrayUnsafe(), "AES"),
          new IvParameterSpec(iv.toArrayUnsafe()));
      return Bytes.wrap(cipher.doFinal(plain.toArrayUnsafe()));
    } catch (Exception e) {
      throw new RuntimeException("No AES/CTR cipher provider", e);
    }
  }

  public static Cipher createAesctrDecryptor(Bytes key, Bytes iv)
      throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        new SecretKeySpec(key.toArrayUnsafe(), "AES"),
        new IvParameterSpec(iv.toArrayUnsafe()));
    return cipher;
  }

  public static Bytes aesctrDecrypt(Bytes key, Bytes iv, Bytes ciphered)
      throws GeneralSecurityException {
    return Bytes.wrap(createAesctrDecryptor(key, iv).doFinal(ciphered.toArrayUnsafe()));
  }
}
