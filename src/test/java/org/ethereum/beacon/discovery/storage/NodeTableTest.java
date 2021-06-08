/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import static org.ethereum.beacon.discovery.TestUtil.NODE_RECORD_FACTORY_NO_VERIFICATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.TestUtil;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordInfo;
import org.ethereum.beacon.discovery.schema.NodeStatus;
import org.junit.jupiter.api.Test;

public class NodeTableTest {
  final String LOCALHOST_BASE64 =
      "-IS4QHCYrYZbAKWCBRlAy5zzaDZXJBGkcnh4MHcBFZntXNFrdvJjX04jRzjzCBOonrkTfj499SZuOh8R33Ls8RRcy5wBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQMRo9bfkceoY0W04hSgYU5Q1R_mmq3Qp9pBPMAIduKrAYN1ZHCCdl8=";

  @Test
  public void testCreate() throws Exception {
    NodeRecord nodeRecord = NODE_RECORD_FACTORY_NO_VERIFICATION.fromBase64(LOCALHOST_BASE64);
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    NodeTableStorage nodeTableStorage = nodeTableStorageFactory.createTable(List.of(nodeRecord));
    Optional<NodeRecordInfo> extendedEnr = nodeTableStorage.get().getNode(nodeRecord.getNodeId());
    assertTrue(extendedEnr.isPresent());
    NodeRecordInfo nodeRecord2 = extendedEnr.get();
    assertEquals(
        nodeRecord.get(EnrField.PKEY_SECP256K1),
        nodeRecord2.getNode().get(EnrField.PKEY_SECP256K1));
  }

  @Test
  public void testFind() throws Exception {
    NodeRecord localHostNode = NODE_RECORD_FACTORY_NO_VERIFICATION.fromBase64(LOCALHOST_BASE64);
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    NodeTableStorage nodeTableStorage = nodeTableStorageFactory.createTable(List.of(localHostNode));

    // node is adjusted to be close to localhostEnr
    NodeRecord closestNode = TestUtil.generateUnverifiedNode(30267).getNodeRecord();
    nodeTableStorage.get().save(new NodeRecordInfo(closestNode, -1L, NodeStatus.ACTIVE, 0));
    assertEquals(
        nodeTableStorage
            .get()
            .getNode(closestNode.getNodeId())
            .get()
            .getNode()
            .get(EnrField.PKEY_SECP256K1),
        closestNode.get(EnrField.PKEY_SECP256K1));
    // node is adjusted to be far from localhostEnr
    NodeRecord farNode = TestUtil.generateUnverifiedNode(30304).getNodeRecord();
    nodeTableStorage.get().save(new NodeRecordInfo(farNode, -1L, NodeStatus.ACTIVE, 0));
    List<NodeRecordInfo> closestNodes =
        nodeTableStorage.get().findClosestNodes(closestNode.getNodeId(), 254);
    assertEquals(2, closestNodes.size());
    Set<Bytes> publicKeys = new HashSet<>();
    closestNodes.forEach(n -> publicKeys.add((Bytes) n.getNode().get(EnrField.PKEY_SECP256K1)));
    assertTrue(publicKeys.contains(localHostNode.get(EnrField.PKEY_SECP256K1)));
    assertTrue(publicKeys.contains(closestNode.get(EnrField.PKEY_SECP256K1)));
    List<NodeRecordInfo> farNodes = nodeTableStorage.get().findClosestNodes(farNode.getNodeId(), 1);
    assertEquals(1, farNodes.size());
    assertEquals(
        farNodes.get(0).getNode().get(EnrField.PKEY_SECP256K1),
        farNode.get(EnrField.PKEY_SECP256K1));
  }

  /**
   * Verifies that calculated index number is in range of [0, {@link
   * NodeTableImpl#NUMBER_OF_INDEXES})
   */
  @Test
  public void testIndexCalculation() {
    Bytes nodeId0 =
        Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000000");
    Bytes nodeId1a =
        Bytes.fromHexString("0000000000000000000000000000000000000000000000000000000000000001");
    Bytes nodeId1b =
        Bytes.fromHexString("1000000000000000000000000000000000000000000000000000000000000000");
    Bytes nodeId1s =
        Bytes.fromHexString("1111111111111111111111111111111111111111111111111111111111111111");
    Bytes nodeId9s =
        Bytes.fromHexString("9999999999999999999999999999999999999999999999999999999999999999");
    Bytes nodeIdfs =
        Bytes.fromHexString("ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff");
    assertEquals(0, NodeTableImpl.getNodeIndex(nodeId0));
    assertEquals(0, NodeTableImpl.getNodeIndex(nodeId1a));
    assertEquals(16, NodeTableImpl.getNodeIndex(nodeId1b));
    assertEquals(17, NodeTableImpl.getNodeIndex(nodeId1s));
    assertEquals(153, NodeTableImpl.getNodeIndex(nodeId9s));
    assertEquals(255, NodeTableImpl.getNodeIndex(nodeIdfs));
  }
}
