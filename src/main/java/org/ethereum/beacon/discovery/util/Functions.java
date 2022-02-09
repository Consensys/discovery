/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.util;

import static org.ethereum.beacon.discovery.util.Utils.extractBytesFromUnsignedBigInt;
import static org.web3j.crypto.Sign.CURVE_PARAMS;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.Arrays;
import org.ethereum.beacon.discovery.type.Hashes;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;

/** Set of cryptography and utilities functions used in discovery */
public class Functions {
  private static final Logger logger = LogManager.getLogger();
  public static final ECDomainParameters SECP256K1_CURVE =
      new ECDomainParameters(
          CURVE_PARAMS.getCurve(), CURVE_PARAMS.getG(), CURVE_PARAMS.getN(), CURVE_PARAMS.getH());
  public static final int PRIVKEY_SIZE = 32;
  public static final int PUBKEY_SIZE = 64;
  private static final int RECIPIENT_KEY_LENGTH = 16;
  private static final int INITIATOR_KEY_LENGTH = 16;
  private static final int AUTH_RESP_KEY_LENGTH = 16;
  private static final int MS_IN_SECOND = 1000;

  private static final Supplier<SecureRandom> SECURE_RANDOM = Suppliers.memoize(SecureRandom::new);

  private static boolean SKIP_SIGNATURE_VERIFY = false;

  /** SHA2 (SHA256) */
  public static Bytes hash(Bytes value) {
    return Hashes.sha256(value);
  }

  /** SHA3 (Keccak256) */
  public static Bytes hashKeccak(Bytes value) {
    return Bytes.wrap(Hash.sha3(value.toArray()));
  }

  /**
   * Creates a signature of message `x` using the given key.
   *
   * @param key private key
   * @param x message, hashed
   * @return ECDSA signature with properties merged together: r || s
   */
  public static Bytes sign(Bytes key, Bytes x) {
    Sign.SignatureData signatureData =
        Sign.signMessage(x.toArray(), ECKeyPair.create(key.toArray()), false);
    Bytes r = Bytes.wrap(signatureData.getR());
    Bytes s = Bytes.wrap(signatureData.getS());
    return Bytes.concatenate(r, s);
  }

  public static void setSkipSignatureVerify(boolean skipSignatureVerify) {
    SKIP_SIGNATURE_VERIFY = skipSignatureVerify;
  }

  /**
   * Verifies that signature is made by signer
   *
   * @param signature Signature, ECDSA
   * @param x message, hashed
   * @param pubKey Public key of supposed signer, compressed, 33 bytes
   * @return whether `signature` reflects message `x` signed with `pubkey`
   */
  public static boolean verifyECDSASignature(Bytes signature, Bytes x, Bytes pubKey) {
    Preconditions.checkArgument(pubKey.size() == 33, "Invalid public key size");

    if (SKIP_SIGNATURE_VERIFY) {
      return true;
    }
    ECPoint ecPoint = Functions.publicKeyToPoint(pubKey);
    Bytes pubKeyUncompressed = Bytes.wrap(ecPoint.getEncoded(false)).slice(1);
    ECDSASignature ecdsaSignature =
        new ECDSASignature(
            new BigInteger(1, signature.slice(0, 32).toArray()),
            new BigInteger(1, signature.slice(32).toArray()));
    try {
      for (int recId = 0; recId < 4; ++recId) {
        BigInteger calculatedPubKey = Sign.recoverFromSignature(recId, ecdsaSignature, x.toArray());
        if (calculatedPubKey == null) {
          continue;
        }
        if (Arrays.areEqual(
            pubKeyUncompressed.toArray(),
            extractBytesFromUnsignedBigInt(calculatedPubKey, PUBKEY_SIZE))) {
          return true;
        }
      }
    } catch (final IllegalArgumentException e) {
      logger.trace("Failed to verify ECDSA signature", e);
      return false;
    }
    return false;
  }

  public static ECKeyPair generateECKeyPair() {
    return generateECKeyPair(Functions.getRandom());
  }

  public static ECKeyPair generateECKeyPair(Random rnd) {
    byte[] keyBytes = new byte[PRIVKEY_SIZE];
    rnd.nextBytes(keyBytes);
    return ECKeyPair.create(keyBytes);
  }

  /** Maps public key to point on {@link #SECP256K1_CURVE} */
  public static ECPoint publicKeyToPoint(Bytes pkey) {
    byte[] destPubPointBytes;
    if (pkey.size() == 64) { // uncompressed
      destPubPointBytes = new byte[pkey.size() + 1];
      destPubPointBytes[0] = 0x04; // default prefix
      System.arraycopy(pkey.toArray(), 0, destPubPointBytes, 1, pkey.size());
    } else {
      destPubPointBytes = pkey.toArray();
    }
    return SECP256K1_CURVE.getCurve().decodePoint(destPubPointBytes);
  }

  /** Derives public key in SECP256K1, compressed */
  public static Bytes derivePublicKeyFromPrivate(Bytes privateKey) {
    ECKeyPair ecKeyPair = ECKeyPair.create(privateKey.toArray());
    return getCompressedPublicKey(ecKeyPair);
  }

  public static Bytes getCompressedPublicKey(ECKeyPair ecKeyPair) {
    final Bytes pubKey =
        Bytes.wrap(extractBytesFromUnsignedBigInt(ecKeyPair.getPublicKey(), PUBKEY_SIZE));
    return compressPublicKey(pubKey);
  }

  public static Bytes compressPublicKey(Bytes uncompressedPublicKey) {
    ECPoint ecPoint = Functions.publicKeyToPoint(uncompressedPublicKey);
    return Bytes.wrap(ecPoint.getEncoded(true));
  }

  /** Derives key agreement ECDH by multiplying private key by public */
  public static Bytes deriveECDHKeyAgreement(Bytes srcPrivKey, Bytes destPubKey) {
    ECPoint pudDestPoint = publicKeyToPoint(destPubKey);
    ECPoint mult = pudDestPoint.multiply(new BigInteger(1, srcPrivKey.toArray()));
    return Bytes.wrap(mult.getEncoded(true));
  }

  /**
   * The ephemeral key is used to perform Diffie-Hellman key agreement with B's static public key
   * and the session keys are derived from it using the HKDF key derivation function.
   *
   * <p><code>
   * ephemeral-key = random private key
   * ephemeral-pubkey = public key corresponding to ephemeral-key
   * dest-pubkey = public key of B
   * secret = agree(ephemeral-key, dest-pubkey)
   * info = "discovery v5 key agreement" || node-id-A || node-id-B
   * prk = HKDF-Extract(secret, id-nonce)
   * initiator-key, recipient-key, auth-resp-key = HKDF-Expand(prk, info)</code>
   */
  public static HKDFKeys hkdf_expand(
      Bytes srcNodeId, Bytes destNodeId, Bytes srcPrivKey, Bytes destPubKey, Bytes idNonce) {
    Bytes keyAgreement = deriveECDHKeyAgreement(srcPrivKey, destPubKey);
    return hkdf_expand(srcNodeId, destNodeId, keyAgreement, idNonce);
  }

  /**
   * {@link #hkdf_expand(Bytes, Bytes, Bytes, Bytes, Bytes)} but with keyAgreement already derived
   * by {@link #deriveECDHKeyAgreement(Bytes, Bytes)}
   */
  @SuppressWarnings({"DefaultCharset"})
  public static HKDFKeys hkdf_expand(
      Bytes srcNodeId, Bytes destNodeId, Bytes keyAgreement, Bytes idNonce) {
    try {
      Bytes info =
          Bytes.concatenate(
              Bytes.wrap("discovery v5 key agreement".getBytes()), srcNodeId, destNodeId);
      HKDFParameters hkdfParameters =
          new HKDFParameters(keyAgreement.toArray(), idNonce.toArray(), info.toArray());
      Digest digest = new SHA256Digest();
      HKDFBytesGenerator hkdfBytesGenerator = new HKDFBytesGenerator(digest);
      hkdfBytesGenerator.init(hkdfParameters);
      // initiator-key || recipient-key || auth-resp-key
      byte[] hkdfOutputBytes =
          new byte[INITIATOR_KEY_LENGTH + RECIPIENT_KEY_LENGTH + AUTH_RESP_KEY_LENGTH];
      hkdfBytesGenerator.generateBytes(
          hkdfOutputBytes, 0, INITIATOR_KEY_LENGTH + RECIPIENT_KEY_LENGTH + AUTH_RESP_KEY_LENGTH);
      Bytes hkdfOutput = Bytes.wrap(hkdfOutputBytes);
      Bytes initiatorKey = hkdfOutput.slice(0, INITIATOR_KEY_LENGTH);
      Bytes recipientKey = hkdfOutput.slice(INITIATOR_KEY_LENGTH, RECIPIENT_KEY_LENGTH);
      Bytes authRespKey = hkdfOutput.slice(INITIATOR_KEY_LENGTH + RECIPIENT_KEY_LENGTH);
      return new HKDFKeys(initiatorKey, recipientKey, authRespKey);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  /** Current time in seconds */
  public static long getTime() {
    return System.currentTimeMillis() / MS_IN_SECOND;
  }

  /** Random provider */
  public static Random getRandom() {
    return SECURE_RANDOM.get();
  }

  /**
   * The 'distance' between two node IDs is the bitwise XOR of the IDs, taken as the number.
   *
   * <p>distance(n₁, n₂) = n₁ XOR n₂
   *
   * <p>LogDistance is reverse of length of common prefix in bits (length - number of leftmost zeros
   * in XOR)
   */
  public static int logDistance(Bytes nodeId1, Bytes nodeId2) {
    Bytes distance = nodeId1.xor(nodeId2);
    int logDistance = Byte.SIZE * distance.size(); // 256
    final int maxLogDistance = logDistance;
    for (int i = 0; i < maxLogDistance; ++i) {
      boolean highBit = ((distance.get(i / 8) >> (7 - (i % 8))) & 1) == 1;
      if (highBit) {
        break;
      } else {
        logDistance--;
      }
    }
    return logDistance;
  }

  public static BigInteger distance(Bytes nodeId1, Bytes nodeId2) {
    return nodeId1.xor(nodeId2).toUnsignedBigInteger(ByteOrder.LITTLE_ENDIAN);
  }

  /**
   * Stores set of keys derived by simple key derivation function (KDF) based on a hash-based
   * message authentication code (HMAC)
   */
  public static class HKDFKeys {
    private final Bytes initiatorKey;
    private final Bytes recipientKey;
    private final Bytes authResponseKey;

    public HKDFKeys(Bytes initiatorKey, Bytes recipientKey, Bytes authResponseKey) {
      this.initiatorKey = initiatorKey;
      this.recipientKey = recipientKey;
      this.authResponseKey = authResponseKey;
    }

    public Bytes getInitiatorKey() {
      return initiatorKey;
    }

    public Bytes getRecipientKey() {
      return recipientKey;
    }

    public Bytes getAuthResponseKey() {
      return authResponseKey;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      HKDFKeys hkdfKeys = (HKDFKeys) o;
      return Objects.equal(initiatorKey, hkdfKeys.initiatorKey)
          && Objects.equal(recipientKey, hkdfKeys.recipientKey)
          && Objects.equal(authResponseKey, hkdfKeys.authResponseKey);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(initiatorKey, recipientKey, authResponseKey);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("initiatorKey", initiatorKey)
          .add("recipientKey", recipientKey)
          .add("authResponseKey", authResponseKey)
          .toString();
    }
  }
}
