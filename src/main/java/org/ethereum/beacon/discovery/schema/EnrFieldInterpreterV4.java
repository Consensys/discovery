/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

public class EnrFieldInterpreterV4 implements EnrFieldInterpreter {
  public static EnrFieldInterpreterV4 DEFAULT = new EnrFieldInterpreterV4();

  private Map<String, Function<RlpString, Object>> fieldDecoders = new HashMap<>();

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
  public Object decode(String key, RlpString rlpString) {
    Function<RlpString, Object> fieldDecoder = fieldDecoders.get(key);
    if (fieldDecoder == null) {
      throw new RuntimeException(String.format("No decoder found for field `%s`", key));
    }
    return fieldDecoder.apply(rlpString);
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
