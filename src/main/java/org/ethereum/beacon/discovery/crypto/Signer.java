/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.crypto;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Abstraction for performing private-key operations.
 *
 * <p>Implementations perform signing and ECDH key agreement without exposing private key material.
 */
public interface Signer {

  /**
   * Creates a signature of message `x`.
   *
   * @param messageHash message, hashed
   * @return ECDSA signature with properties merged together: r || s
   */
  Bytes sign(final Bytes32 messageHash);

  /**
   * Derives a shared secret using ECDH with the given peer public key.
   *
   * @param destPubKey the destination peer's public key
   * @return the derived shared secret
   */
  Bytes deriveECDHKeyAgreement(Bytes destPubKey);

  /**
   * Derives the compressed public key corresponding to the private key held by this module.
   *
   * @return the compressed public key
   */
  Bytes deriveCompressedPublicKeyFromPrivate();
}
