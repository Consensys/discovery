/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.v2.bytes.Bytes;
import org.ethereum.beacon.discovery.util.DecodeException;

/**
 * Abstract packet consisting of header and message bytes
 *
 * <p>{@code packet = masking-iv || masked-header || message}
 *
 * <p>In the scheme above the {@link Packet} represents {@code masked-header || message } part with
 * decrypted (AES/CTR) masked-header
 */
public interface Packet<TAuthData extends AuthData> extends BytesSerializable {

  Bytes getMessageCyphered();

  Header<TAuthData> getHeader();

  @Override
  default void validate() throws DecodeException {
    getHeader().validate();
  }
}
