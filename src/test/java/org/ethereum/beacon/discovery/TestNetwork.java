/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery;

import java.util.HashMap;
import java.util.Map;

public class TestNetwork {

  private final Map<Integer, TestManagerWrapper> nodes = new HashMap<>();

  public TestManagerWrapper createDiscoveryManager(int seed) {
    TestManagerWrapper ret = TestManagerWrapper.create(this, seed);
    nodes.put(ret.getPort(), ret);
    return ret;
  }

  public TestManagerWrapper find(int port) {
    return nodes.get(port);
  }
}
