/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;

/** Node Index. Stores several node keys. */
public class NodeIndex {
  private final List<Bytes> entries = new ArrayList<>();

  public List<Bytes> getEntries() {
    return entries;
  }
}
