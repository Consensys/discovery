/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.crypto;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.util.Functions;

/** In-memory {@link NodeKeyService} implementation backed by a SECP256K1 {@link SecretKey}. */
public class InMemoryNodeKeyService implements NodeKeyService {
  private final SecretKey secretKey;

  private InMemoryNodeKeyService(final SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  public static InMemoryNodeKeyService create(final SecretKey secretKey) {
    return new InMemoryNodeKeyService(secretKey);
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
