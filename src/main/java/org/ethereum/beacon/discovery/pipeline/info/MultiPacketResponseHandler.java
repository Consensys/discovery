/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.pipeline.info;

import org.ethereum.beacon.discovery.message.V5Message;
import org.ethereum.beacon.discovery.schema.NodeSession;

public interface MultiPacketResponseHandler<TMessageType extends V5Message> {

  MultiPacketResponseHandler<?> SINGLE_PACKET_RESPONSE_HANDLER = (msg, session) -> true;

  /**
   * Handle next packet in a single response
   *
   * @return true if all expected packets are received, false otherwise
   */
  boolean handleResponseMessage(TMessageType msg, NodeSession session);
}
