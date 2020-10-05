/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.pipeline.info;

import org.ethereum.beacon.discovery.message.V5Message;

public interface MultiPacketResponseHandler<TMessageType extends V5Message> {

  MultiPacketResponseHandler<?> SINGLE_PACKET_RESPONSE_HANDLER = __ -> true;

  /**
   * Handle next packet in a single response
   *
   * @return true if all expected packets are received, false otherwise
   */
  boolean handleResponseMessage(TMessageType msg);
}
