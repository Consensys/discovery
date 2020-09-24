/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.util;

public class DecryptException extends DecodeException {

  public DecryptException(String message) {
    super(message);
  }

  public DecryptException(String message, Throwable cause) {
    super(message, cause);
  }
}
