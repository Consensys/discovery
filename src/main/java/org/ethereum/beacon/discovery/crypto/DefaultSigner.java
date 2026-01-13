/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.crypto;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.util.Functions;

/** In-memory {@link Signer} implementation backed by a SECP256K1 {@link SecretKey}. */
public class DefaultSigner implements Signer {
  private final SecretKey secretKey;

  public DefaultSigner(final SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  public static DefaultSigner create(final SecretKey secretKey) {
    return new DefaultSigner(secretKey);
  }

  /** {@inheritDoc} */
  @Override
  public Bytes deriveECDHKeyAgreement(final Bytes peerPublicKey) {
    return Functions.deriveECDHKeyAgreement(secretKey, peerPublicKey);
  }

  /**
   * Creates a signature of message `x`.
   *
   * @param messageHash message, hashed
   * @return ECDSA signature with properties merged together: r || s
   */
  @Override
  public Bytes sign(final Bytes32 messageHash) {
    final SECP256K1.KeyPair keyPair = SECP256K1.KeyPair.fromSecretKey(secretKey);
    final SECP256K1.Signature signature = SECP256K1.signHashed(messageHash, keyPair);
    // cutting v
    return signature.bytes().slice(0, 64);
  }

  /** {@inheritDoc} */
  @Override
  public Bytes deriveCompressedPublicKeyFromPrivate() {
    final SECP256K1.PublicKey publicKey = SECP256K1.PublicKey.fromSecretKey(secretKey);
    return Bytes.wrap(publicKey.asEcPoint().getEncoded(true));
  }
}
