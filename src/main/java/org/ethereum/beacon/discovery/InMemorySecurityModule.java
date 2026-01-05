/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.util.Functions;

/**
 * Local {@link SecurityModule} implementation backed by an in-memory SECP256K1 {@link SecretKey}.
 *
 * <p>This service provides cryptographic operations required by the discovery pipeline,
 * specifically:
 *
 * <ul>
 *   <li>ECDH key agreement for secure peer communication
 *   <li>Message signing using the node's private key
 * </ul>
 */
public class InMemorySecurityModule implements SecurityModule {

  /** The node's local SECP256K1 private key used for signing and key agreement. */
  private final SecretKey secretKey;

  /**
   * Creates a new {@code LocalNodeCryptoService} backed by the provided secret key.
   *
   * @param secretKey the local SECP256K1 private key representing the node identity
   */
  public InMemorySecurityModule(final SecretKey secretKey) {
    this.secretKey = secretKey;
  }

  /**
   * Creates a new {@code InMemorySecurityModule} backed by the provided secret key.
   *
   * @param secretKey the local SECP256K1 private key representing the node identity
   * @return the created security module
   */
  public static InMemorySecurityModule create(final SecretKey secretKey) {
    return new InMemorySecurityModule(secretKey);
  }

  /**
   * Derives a shared secret using ECDH with the node's private key and the destination peer's
   * public key.
   *
   * @param destPubKey the destination peer's public key
   * @return the derived shared secret
   */
  @Override
  public Bytes deriveECDHKeyAgreement(final Bytes destPubKey) {
    return Functions.deriveECDHKeyAgreement(secretKey, destPubKey);
  }

  /**
   * Signs the given message hash using the node's private key.
   *
   * @param messageHash the 32-byte hash of the message to sign
   * @return the cryptographic signature
   */
  @Override
  public Bytes sign(final Bytes32 messageHash) {
    return Functions.sign(secretKey, messageHash);
  }

  @Override
  public Bytes deriveCompressedPublicKeyFromPrivate() {
    return Functions.deriveCompressedPublicKeyFromPrivate(secretKey);
  }
}
