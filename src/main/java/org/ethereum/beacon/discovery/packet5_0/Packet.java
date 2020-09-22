/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.packet5_0;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.ethereum.beacon.discovery.util.Functions;

/**
 * Network packet as defined by discovery v5 specification. See <a
 * href="https://github.com/ethereum/devp2p/blob/master/discv5/discv5-wire.md#packet-encoding">https://github.com/ethereum/devp2p/blob/master/discv5/discv5-wire.md#packet-encoding</a>
 */
public interface Packet {

  static Bytes createTag(Bytes homeNodeId, Bytes destNodeId) {
    return homeNodeId.xor(Functions.hash(destNodeId), MutableBytes.create(destNodeId.size()));
  }

  Bytes getBytes();
}
