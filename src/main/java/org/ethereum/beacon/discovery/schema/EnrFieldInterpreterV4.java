/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

public class EnrFieldInterpreterV4 implements EnrFieldInterpreter {

  public static final Function<RlpString, Object> DEFAULT_DECODER =
      rlp -> Bytes.wrap(rlp.getBytes());
  public static final EnrFieldInterpreterV4 DEFAULT = new EnrFieldInterpreterV4();

  private final Map<String, Function<RlpString, Object>> fieldDecoders = new HashMap<>();

  @SuppressWarnings({"DefaultCharset"})
  public EnrFieldInterpreterV4() {
    fieldDecoders.put(EnrField.PKEY_SECP256K1, rlpString -> Bytes.wrap(rlpString.getBytes()));
    fieldDecoders.put(
        EnrField.ID, rlpString -> IdentitySchema.fromString(new String(rlpString.getBytes())));
    fieldDecoders.put(EnrField.IP_V4, rlpString -> Bytes.wrap(rlpString.getBytes()));
    fieldDecoders.put(EnrField.TCP, rlpString -> rlpString.asPositiveBigInteger().intValue());
    fieldDecoders.put(EnrField.UDP, rlpString -> rlpString.asPositiveBigInteger().intValue());
    fieldDecoders.put(EnrField.IP_V6, rlpString -> Bytes.wrap(rlpString.getBytes()));
    fieldDecoders.put(EnrField.TCP_V6, rlpString -> rlpString.asPositiveBigInteger().intValue());
    fieldDecoders.put(EnrField.UDP_V6, rlpString -> rlpString.asPositiveBigInteger().intValue());
  }

  @Override
  public Object decode(String key, RlpType rlpType) {
    if (rlpType instanceof RlpList) {
      return ((RlpList) rlpType)
          .getValues().stream()
              .map(v -> DEFAULT_DECODER.apply((RlpString) v))
              .collect(Collectors.toList());
    }

    Function<RlpString, Object> fieldDecoder = fieldDecoders.getOrDefault(key, DEFAULT_DECODER);
    return fieldDecoder.apply((RlpString) rlpType);
  }

  @Override
  public RlpType encode(String key, Object object) {
    return RlpUtil.encode(
        object,
        o ->
            String.format(
                "Couldn't encode field %s with value %s of type %s: no serializer found.",
                key, object, object.getClass()));
  }
}
