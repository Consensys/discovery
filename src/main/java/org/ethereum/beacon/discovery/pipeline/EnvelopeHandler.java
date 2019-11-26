/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.pipeline;

public interface EnvelopeHandler {
  void handle(Envelope envelope);
}
