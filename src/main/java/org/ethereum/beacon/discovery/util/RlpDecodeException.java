/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.util;

public class RlpDecodeException extends RuntimeException {

  public RlpDecodeException(String message) {
    super(message);
  }

  public RlpDecodeException(String message, Throwable cause) {
    super(message, cause);
  }
}
