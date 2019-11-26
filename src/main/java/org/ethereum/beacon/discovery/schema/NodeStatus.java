/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import java.util.HashMap;
import java.util.Map;

/** Status of {@link org.ethereum.beacon.discovery.schema.NodeRecord} */
public enum NodeStatus {
  ACTIVE(0x01), // Alive
  SLEEP(0x02), // Didn't answer last time(s)
  DEAD(0x03); // Didnt' answer for a long time

  private static final Map<Integer, NodeStatus> codeMap = new HashMap<>();

  static {
    for (NodeStatus type : NodeStatus.values()) {
      codeMap.put(type.code, type);
    }
  }

  private int code;

  NodeStatus(int code) {
    this.code = code;
  }

  public static NodeStatus fromNumber(int i) {
    return codeMap.get(i);
  }

  public byte byteCode() {
    return (byte) code;
  }
}
