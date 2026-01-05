/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.crypto;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.util.Functions;

/** In-memory {@link NodeKeyHolder} implementation backed by a SECP256K1 {@link SecretKey}. */
public class InMemoryNodeKeyHolder implements NodeKeyHolder {

  /** The SECP256K1 private key representing the node's identity. */
  private final SecretKey secretKey;

  public InMemoryNodeKeyHolder(final SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  public static InMemoryNodeKeyHolder create(final SecretKey secretKey) {
    return new InMemoryNodeKeyHolder(secretKey);
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
