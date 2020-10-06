/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.processor;

import org.ethereum.beacon.discovery.message.Message;
import org.ethereum.beacon.discovery.schema.DiscoveryProtocol;
import org.ethereum.beacon.discovery.schema.NodeSession;

/** Handles discovery messages of several types */
public interface DiscoveryMessageProcessor<M extends Message> {
  DiscoveryProtocol getSupportedIdentity();

  void handleMessage(M message, NodeSession session);
}
