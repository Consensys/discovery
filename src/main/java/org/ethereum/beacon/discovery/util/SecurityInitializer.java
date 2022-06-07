/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.util;

import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Adds BouncyCastle "BC" to the list of available security providers. Run {@link #init()} before
 * any usage of BouncyCastle
 */
public class SecurityInitializer {
  private static final BouncyCastleProvider PROVIDER = new BouncyCastleProvider();

  public static void init() {
    Security.addProvider(PROVIDER);
  }
}
