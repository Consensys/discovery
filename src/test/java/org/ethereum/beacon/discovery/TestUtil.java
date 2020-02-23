/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
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
   * same port. Signature is not valid if verified.
   *
   * @return <code><private key, node record></code>
   */
  public static NodeInfo generateUnverifiedNode(int port) {
    return generateNode(port, false);
  }

  /**
   * Generates node on 127.0.0.1 with provided port. Node key is random, but always the same for the
   * same port.
   *
   * @param port listen port
   * @param verification whether to use valid signature
   * @return <code><private key, node record></code>
   */
  public static NodeInfo generateNode(int port, boolean verification) {
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

    nodeRecord.sign(Bytes.wrap(privateKey));
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
