/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import java.util.HashMap;
import java.util.Map;

/** Discovery protocol identity schemes */
public enum IdentityScheme {
  V4("v4"),
  V5("v5");

  private static final Map<String, IdentityScheme> nameMap = new HashMap<>();

  static {
    for (IdentityScheme scheme : IdentityScheme.values()) {
      nameMap.put(scheme.name, scheme);
    }
  }

  private final String name;

  private IdentityScheme(String name) {
    this.name = name;
  }

  public static IdentityScheme fromString(String name) {
    return nameMap.get(name);
  }

  public String stringName() {
    return name;
  }
}
