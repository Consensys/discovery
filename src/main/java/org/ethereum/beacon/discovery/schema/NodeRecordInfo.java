/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import static org.ethereum.beacon.discovery.util.RlpUtil.CONS_ANY;
import static org.ethereum.beacon.discovery.util.RlpUtil.CONS_UINT64;

import com.google.common.base.Objects;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.ethereum.beacon.discovery.util.Utils;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

/**
 * Container for {@link NodeRecord}. Also saves all necessary data about presence of this node and
 * last test of its availability
 */
public class NodeRecordInfo {
  private final NodeRecord node;
  private final Long lastRetry;
  private final NodeStatus status;
  private final Integer retry;

  public NodeRecordInfo(NodeRecord node, Long lastRetry, NodeStatus status, Integer retry) {
    this.node = node;
    this.lastRetry = lastRetry;
    this.status = status;
    this.retry = retry;
  }

  public static NodeRecordInfo createDefault(NodeRecord nodeRecord) {
    return new NodeRecordInfo(nodeRecord, -1L, NodeStatus.ACTIVE, 0);
  }

  public static NodeRecordInfo fromRlpBytes(Bytes bytes, NodeRecordFactory nodeRecordFactory) {
    List<Bytes> bytesList =
        RlpUtil.decodeListOfStrings(bytes, CONS_ANY, CONS_UINT64, CONS_ANY, CONS_UINT64);
    return new NodeRecordInfo(
        nodeRecordFactory.fromBytes(bytesList.get(0)),
        Utils.toUInt64(bytesList.get(1)).toLong(),
        NodeStatus.fromNumber(bytesList.get(2).get(0)),
        Utils.toUInt64(bytesList.get(3)).intValue());
  }

  public Bytes toRlpBytes() {
    List<RlpType> values = new ArrayList<>();
    values.add(RlpString.create(getNode().serialize().toArray()));
    values.add(RlpString.create(getLastRetry()));
    values.add(RlpString.create(getStatus().byteCode()));
    values.add(RlpString.create(getRetry()));
    byte[] bytes = RlpEncoder.encode(new RlpList(values));
    return Bytes.wrap(bytes);
  }

  public NodeRecord getNode() {
    return node;
  }

  public Long getLastRetry() {
    return lastRetry;
  }

  public NodeStatus getStatus() {
    return status;
  }

  public Integer getRetry() {
    return retry;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NodeRecordInfo that = (NodeRecordInfo) o;
    return Objects.equal(node, that.node)
        && Objects.equal(lastRetry, that.lastRetry)
        && status == that.status
        && Objects.equal(retry, that.retry);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(node, lastRetry, status, retry);
  }

  @Override
  public String toString() {
    return "NodeRecordInfo{"
        + "node="
        + node
        + ", lastRetry="
        + lastRetry
        + ", status="
        + status
        + ", retry="
        + retry
        + '}';
  }
}
