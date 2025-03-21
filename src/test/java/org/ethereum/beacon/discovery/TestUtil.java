/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.v2.bytes.Bytes;
import org.apache.tuweni.v2.bytes.Bytes32;
import org.apache.tuweni.v2.crypto.SECP256K1.KeyPair;
import org.apache.tuweni.v2.crypto.SECP256K1.SecretKey;
import org.ethereum.beacon.discovery.message.DiscoveryV5MessageDecoder;
import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.mock.IdentitySchemaV4InterpreterMock;
import org.ethereum.beacon.discovery.packet.HandshakeMessagePacket.HandshakeAuthData;
import org.ethereum.beacon.discovery.packet.StaticHeader;
import org.ethereum.beacon.discovery.packet.WhoAreYouPacket.WhoAreYouAuthData;
import org.ethereum.beacon.discovery.schema.IdentitySchemaV4Interpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.RlpDecodeException;

public class TestUtil {
  public static final NodeRecordFactory NODE_RECORD_FACTORY =
      new NodeRecordFactory(new IdentitySchemaV4Interpreter());
  public static final NodeRecordFactory NODE_RECORD_FACTORY_NO_VERIFICATION =
      new NodeRecordFactory(
          new IdentitySchemaV4InterpreterMock()); // doesn't verify ECDSA signature
  public static final String LOCALHOST = "127.0.0.1";
  static final int SEED = 123456789;
  public static final int TEST_TRAFFIC_READ_LIMIT = 250000;

  /**
   * Generates node on 127.0.0.1 with provided port. Node key is random, but always the same for the
   * same port. Signature is not validated.
   *
   * <p>return { @code (privateKey, nodeRecord) }
   */
  public static NodeInfo generateUnverifiedNode(int port) {
    return generateNode(port, false, true);
  }

  /**
   * Generates node on 127.0.0.1 with provided port. Node key is random, but always the same for the
   * same port. Validation will fail.
   *
   * <p>return { @code (privateKey, nodeRecord) }
   */
  public static NodeInfo generateInvalidNode(int port) {
    return generateNode(port, true, false);
  }

  /**
   * Generates node on 127.0.0.1 with provided port. Node key is random, but always the same for the
   * same port.
   *
   * @param port listen port return { @code (privateKey, nodeRecord) }
   */
  public static NodeInfo generateNode(int port) {
    return generateNode(port, true, true);
  }

  /**
   * Generates node on 127.0.0.1 with provided port. Node key is random, but always the same for the
   * same port.
   *
   * @param port listen port
   * @param verification whether to verify signatures
   * @param sign whether or not to sign the record return { @code (privateKey, nodeRecord) }
   */
  private static NodeInfo generateNode(int port, boolean verification, boolean sign) {
    final Random rnd = new Random(SEED);
    for (int i = 0; i < port; ++i) {
      rnd.nextBoolean(); // skip according to input
    }
    KeyPair keyPair = Functions.randomKeyPair();

    NodeRecord nodeRecord =
        new NodeRecordBuilder()
            .seq(1)
            .nodeRecordFactory(
                verification ? NODE_RECORD_FACTORY : NODE_RECORD_FACTORY_NO_VERIFICATION)
            .secretKey(keyPair.secretKey())
            .address(LOCALHOST, port)
            .build();

    if (!sign) {
      nodeRecord.setSignature(Bytes32.ZERO);
    }
    return new NodeInfo(keyPair.secretKey(), nodeRecord);
  }

  public static class NodeInfo {
    private final SecretKey secretKey;
    private final NodeRecord nodeRecord;

    public NodeInfo(final SecretKey secretKey, final NodeRecord nodeRecord) {
      this.secretKey = secretKey;
      this.nodeRecord = nodeRecord;
    }

    public SecretKey getSecretKey() {
      return secretKey;
    }

    public NodeRecord getNodeRecord() {
      return nodeRecord;
    }
  }

  public static boolean isFieldsEqual(StaticHeader that, StaticHeader other) {
    return that.getProtocolId().equals(other.getProtocolId())
        && that.getVersion().equals(other.getVersion())
        && that.getFlag().equals(other.getFlag())
        && that.getAuthDataSize() == other.getAuthDataSize();
  }

  public static boolean isFieldsEqual(WhoAreYouAuthData that, WhoAreYouAuthData other) {
    return that.getIdNonce().equals(other.getIdNonce())
        && that.getEnrSeq().equals(other.getEnrSeq());
  }

  public static boolean isFieldsEqual(
      HandshakeAuthData that, HandshakeAuthData other, NodeRecordFactory nodeRecordFactory) {

    return that.getSourceNodeId().equals(other.getSourceNodeId())
        && that.getEphemeralPubKey().equals(other.getEphemeralPubKey())
        && that.getIdSignature().equals(other.getIdSignature())
        && that.getNodeRecord(nodeRecordFactory).equals(other.getNodeRecord(nodeRecordFactory));
  }

  public static void waitFor(final CompletableFuture<?> future) throws Exception {
    waitFor(future, 30);
  }

  public static void waitFor(final CompletableFuture<?> future, final int timeout)
      throws Exception {
    future.get(timeout, TimeUnit.SECONDS);
  }

  public static void waitFor(final ThrowingRunnable assertion) throws Exception {
    int attempts = 0;
    while (true) {
      try {
        assertion.run();
        return;
      } catch (Throwable t) {
        if (attempts < 60) {
          attempts++;
          Thread.sleep(1000);
        } else {
          throw t;
        }
      }
    }
  }

  public static NodeRecord createNodeAtDistance(final Bytes sourceNode, final int distance) {
    final BitSet bits = BitSet.valueOf(sourceNode.mutableCopy().reverse().toArray());
    bits.flip(distance - 1);
    final byte[] targetNodeId = new byte[sourceNode.size()];
    final byte[] src = bits.toByteArray();
    System.arraycopy(src, 0, targetNodeId, 0, src.length);
    final Bytes nodeId = Bytes.wrap(targetNodeId).mutableCopy().reverse();
    return SimpleIdentitySchemaInterpreter.createNodeRecord(
        nodeId, new InetSocketAddress("127.0.0.1", 2));
  }

  public interface ThrowingRunnable {

    void run() throws Exception;
  }

  @SuppressWarnings("unchecked")
  public static <M extends V5Message> void assertRoundTrip(
      final DiscoveryV5MessageDecoder decoder, final M message) {
    final Bytes rlp = message.getBytes();
    final M result = (M) decoder.decode(rlp);
    assertThat(result).isEqualTo(message);
    assertThat(result.getBytes()).isEqualTo(rlp);
  }

  public static <M extends V5Message> void assertRejectTrailingBytes(
      final DiscoveryV5MessageDecoder decoder, final M message) {
    final Bytes rlp = Bytes.wrap(message.getBytes(), Bytes.fromHexString("0x1234"));
    assertThatThrownBy(() -> decoder.decode(rlp)).isInstanceOf(RlpDecodeException.class);
  }
}
