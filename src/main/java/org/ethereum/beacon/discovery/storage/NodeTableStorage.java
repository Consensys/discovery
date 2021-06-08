/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

/** Stores {@link NodeTable} and home node info */
public interface NodeTableStorage {
  NodeTable get();
}
