/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.format.SerializerFactory;
import org.ethereum.beacon.discovery.mock.IdentitySchemaV4InterpreterMock;
import org.ethereum.beacon.discovery.schema.IdentitySchemaV4Interpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.storage.NodeSerializerFactory;

public class TestUtil {
  public static final NodeRecordFactory NODE_RECORD_FACTORY =
      new NodeRecordFactory(new IdentitySchemaV4Interpreter());
  public static final NodeRecordFactory NODE_RECORD_FACTORY_NO_VERIFICATION =
      new NodeRecordFactory(
          new IdentitySchemaV4InterpreterMock()); // doesn't verify ECDSA signature
  public static final SerializerFactory TEST_SERIALIZER =
      new NodeSerializerFactory(NODE_RECORD_FACTORY_NO_VERIFICATION);
  public static final String LOCALHOST = "127.0.0.1";
  static final int SEED = 123456789;

  /**
   * Generates node on 127.0.0.1 with provided port. Node key is random, but always the same for the
   * same port. Signature is not validated.
   *
   * @return <code><private key, node record></code>
   */
  public static NodeInfo generateUnverifiedNode(int port) {
    return generateNode(port, false, true);
  }

  /**
   * Generates node on 127.0.0.1 with provided port. Node key is random, but always the same for the
   * same port. Validation will fail.
   *
   * @return <code><private key, node record></code>
   */
  public static NodeInfo generateInvalidNode(int port) {
    return generateNode(port, true, false);
  }

  /**
   * Generates node on 127.0.0.1 with provided port. Node key is random, but always the same for the
   * same port.
   *
   * @param port listen port
   * @return <code><private key, node record></code>
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
   * @param sign whether or not to sign the record
   * @return <code><private key, node record></code>
   */
  private static NodeInfo generateNode(int port, boolean verification, boolean sign) {
    final Random rnd = new Random(SEED);
    for (int i = 0; i < port; ++i) {
      rnd.nextBoolean(); // skip according to input
    }
    byte[] privateKey = new byte[32];
    rnd.nextBytes(privateKey);

    NodeRecord nodeRecord =
        new NodeRecordBuilder()
            .seq(1)
            .nodeRecordFactory(
                verification ? NODE_RECORD_FACTORY : NODE_RECORD_FACTORY_NO_VERIFICATION)
            .privateKey(Bytes.wrap(privateKey))
            .address(LOCALHOST, port)
            .build();

    if (!sign) {
      nodeRecord.setSignature(Bytes32.ZERO);
    }
    return new NodeInfo(Bytes.wrap(privateKey), nodeRecord);
  }

  public static class NodeInfo {
    private final Bytes privateKey;
    private final NodeRecord nodeRecord;

    public NodeInfo(final Bytes privateKey, final NodeRecord nodeRecord) {
      this.privateKey = privateKey;
      this.nodeRecord = nodeRecord;
    }

    public Bytes getPrivateKey() {
      return privateKey;
    }

    public NodeRecord getNodeRecord() {
      return nodeRecord;
    }
  }
}
