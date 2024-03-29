/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import java.util.HashMap;
import java.util.Map;

/** Available identity schemas of Ethereum {@link NodeRecord} signature */
public enum IdentitySchema {
  V4("v4");

  private static final Map<String, IdentitySchema> NAME_MAP = new HashMap<>();

  static {
    for (IdentitySchema scheme : IdentitySchema.values()) {
      NAME_MAP.put(scheme.name, scheme);
    }
  }

  private final String name;

  private IdentitySchema(String name) {
    this.name = name;
  }

  public static IdentitySchema fromString(String name) {
    return NAME_MAP.get(name);
  }

  public String stringName() {
    return name;
  }
}
