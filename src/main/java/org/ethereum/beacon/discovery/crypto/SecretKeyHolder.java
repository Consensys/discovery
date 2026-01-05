/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.crypto;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Provides cryptographic operations for the local node in the discovery system.
 *
 * <p>Implementations perform signing and ECDH key agreement without exposing private key material.
 * Keys may be held in-memory, external services, or hardware modules.
 */
public interface SecretKeyHolder {

  /**
   * Derives a shared secret using ECDH with the given peer public key.
   *
   * @param destPubKey the destination peer's public key
   * @return the derived shared secret
   */
  Bytes deriveECDHKeyAgreement(Bytes destPubKey);

  /**
   * Signs a 32-byte message hash.
   *
   * @param messageHash the hash of the message to sign
   * @return the signature
   */
  Bytes sign(final Bytes32 messageHash);

  /**
   * Derives the compressed public key corresponding to the private key held by this module.
   *
   * @return the compressed public key
   */
  Bytes deriveCompressedPublicKeyFromPrivate();
}
