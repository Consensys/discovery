/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.scheduler;

/** The same as standard <code>Runnable</code> which can throw unchecked exception */
public interface RunnableEx {
  void run() throws Exception;
}
