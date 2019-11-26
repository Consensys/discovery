/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.processor;

import org.ethereum.beacon.discovery.message.DiscoveryMessage;
import org.ethereum.beacon.discovery.schema.NodeSession;
import org.ethereum.beacon.discovery.schema.Protocol;

/** Handles discovery messages of several types */
public interface DiscoveryMessageProcessor<M extends DiscoveryMessage> {
  Protocol getSupportedIdentity();

  void handleMessage(M message, NodeSession session);
}
