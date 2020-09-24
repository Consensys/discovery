/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message.handler;

import org.ethereum.beacon.discovery.schema.NodeSession;

public interface MessageHandler<Message> {

  void handle(Message message, NodeSession session);
}
