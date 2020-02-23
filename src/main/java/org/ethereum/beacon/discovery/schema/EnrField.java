/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import com.google.common.base.MoreObjects;
import java.util.Objects;

/** Fields of Ethereum Node Record */
public class EnrField {
  // Schema id
  public static final String ID = "id";
  // IPv4 address
  public static final String IP_V4 = "ip";
  // TCP port, integer
  public static final String TCP_V4 = "tcp";
  // UDP port, integer
  public static final String UDP_V4 = "udp";
  // IPv6 address
  public static final String IP_V6 = "ip6";
  // IPv6-specific TCP port
  public static final String TCP_V6 = "tcp6";
  // IPv6-specific UDP port
  public static final String UDP_V6 = "udp6";
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
