/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.util;

public class RlpDecodeException extends DecodeException {

  public RlpDecodeException(String message) {
    super(message);
  }

  public RlpDecodeException(String message, Throwable cause) {
    super(message, cause);
  }
}
