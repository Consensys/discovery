/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.crypto;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.util.Functions;

/** In-memory {@link SecretKeyHolder} implementation backed by a SECP256K1 {@link SecretKey}. */
public class InMemorySecretKeyHolder implements SecretKeyHolder {

  /** The private key */
  private final SecretKey secretKey;

  public InMemorySecretKeyHolder(final SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  public static InMemorySecretKeyHolder create(final SecretKey secretKey) {
    return new InMemorySecretKeyHolder(secretKey);
  }

  /** {@inheritDoc} */
  @Override
  public Bytes deriveECDHKeyAgreement(final Bytes peerPublicKey) {
    return Functions.deriveECDHKeyAgreement(secretKey, peerPublicKey);
  }

  /** {@inheritDoc} */
  @Override
  public Bytes sign(final Bytes32 messageHash) {
    return Functions.sign(secretKey, messageHash);
  }

  /** {@inheritDoc} */
  @Override
  public Bytes deriveCompressedPublicKeyFromPrivate() {
    return Functions.deriveCompressedPublicKeyFromPrivate(secretKey);
  }
}
