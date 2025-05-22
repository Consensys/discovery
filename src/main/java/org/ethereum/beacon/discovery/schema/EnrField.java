/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import com.google.common.base.MoreObjects;
import java.util.Objects;

/**
 * Fields of <a href="https://github.com/ethereum/devp2p/blob/master/enr.md">Ethereum Node
 * Record</a>.
 */
public class EnrField {
  // Schema id
  public static final String ID = "id";
  // IPv4 address
  public static final String IP_V4 = "ip";
  // TCP port, integer
  public static final String TCP = "tcp";
  // UDP port, integer
  public static final String UDP = "udp";
  // QUIC (UDP) port, integer
  public static final String QUIC = "quic";
  // IPv6 address
  public static final String IP_V6 = "ip6";
  // IPv6-specific TCP port
  public static final String TCP_V6 = "tcp6";
  // IPv6-specific UDP port
  public static final String UDP_V6 = "udp6";
  // IPv6-specific QUIC (UDP) port
  public static final String QUIC_V6 = "quic6";

  /* ENR v4 Identity Schema */
  // Compressed secp256k1 public key, 33 bytes
  public static final String PKEY_SECP256K1 = "secp256k1";

  private final String name;
  private final Object value;

  public EnrField(final String name, final Object value) {
    this.name = name;
    this.value = value;
  }

  public String getName() {
    return name;
  }

  public Object getValue() {
    return value;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final EnrField enrField = (EnrField) o;
    return Objects.equals(name, enrField.name) && Objects.equals(value, enrField.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("name", name).add("value", value).toString();
  }
}
