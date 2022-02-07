/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.util.RlpDecodeException;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.web3j.rlp.RlpType;

public class NodeRecordFactory {
  public static final NodeRecordFactory DEFAULT =
      new NodeRecordFactory(new IdentitySchemaV4Interpreter());
  private static final int MAX_ENR_RLP_SIZE = 300;
  private static final int MAX_FIELD_KEY_SIZE = MAX_ENR_RLP_SIZE;

  Map<IdentitySchema, IdentitySchemaInterpreter> interpreters = new HashMap<>();

  public NodeRecordFactory(IdentitySchemaInterpreter... identitySchemaInterpreters) {
    for (IdentitySchemaInterpreter identitySchemaInterpreter : identitySchemaInterpreters) {
      interpreters.put(identitySchemaInterpreter.getScheme(), identitySchemaInterpreter);
    }
  }

  public final NodeRecord createFromValues(UInt64 seq, EnrField... fieldKeyPairs) {
    return createFromValues(seq, Arrays.asList(fieldKeyPairs));
  }

  public NodeRecord createFromValues(UInt64 seq, List<EnrField> fieldKeyPairs) {
    EnrField schemePair = null;
    for (EnrField pair : fieldKeyPairs) {
      if (EnrField.ID.equals(pair.getName())) {
        schemePair = pair;
        break;
      }
    }
    if (schemePair == null) {
      throw new RuntimeException("ENR scheme (ID) is not defined in key-value pairs");
    }

    IdentitySchemaInterpreter identitySchemaInterpreter = interpreters.get(schemePair.getValue());
    if (identitySchemaInterpreter == null) {
      throw new RuntimeException(
          String.format(
              "No ethereum record interpreter found for identity scheme %s",
              schemePair.getValue()));
    }

    return NodeRecord.fromValues(identitySchemaInterpreter, seq, fieldKeyPairs);
  }

  public NodeRecord fromBase64(String enrBase64) {
    return fromBytes(Base64.getUrlDecoder().decode(enrBase64));
  }

  public NodeRecord fromEnr(String enr) {
    return fromBase64(enr.startsWith("enr:") ? enr.substring("enr:".length()) : enr);
  }

  public NodeRecord fromBytes(Bytes bytes) {
    return fromBytes(bytes.toArray());
  }

  @SuppressWarnings({"DefaultCharset"})
  public NodeRecord fromRlpList(List<RlpType> rlpList) {
    if (rlpList.size() < 4) {
      throw new RlpDecodeException(
          String.format("Unable to deserialize ENR with less than 4 fields, [%s]", rlpList));
    }

    // TODO: repair as id is not first now
    IdentitySchema nodeIdentity = null;
    boolean idFound = false;
    for (int i = 2; i < rlpList.size() - 1; i += 2) {
      Bytes id = RlpUtil.asString(rlpList.get(i), RlpUtil.maxSize(MAX_FIELD_KEY_SIZE));
      if (!"id".equals(new String(id.toArrayUnsafe(), StandardCharsets.UTF_8))) {
        continue;
      }

      Bytes idVersion = RlpUtil.asString(rlpList.get(i + 1), RlpUtil.maxSize(MAX_ENR_RLP_SIZE));
      String verString = new String(idVersion.toArrayUnsafe(), StandardCharsets.UTF_8);
      nodeIdentity = IdentitySchema.fromString(verString);
      if (nodeIdentity == null) { // no interpreter for such id
        throw new DecodeException(
            String.format(
                "Unknown node identity scheme '%s', couldn't create node record.", verString));
      }
      idFound = true;
      break;
    }
    if (!idFound) { // no `id` key-values
      throw new DecodeException("Unknown node identity scheme, not defined in record ");
    }

    IdentitySchemaInterpreter identitySchemaInterpreter = interpreters.get(nodeIdentity);
    if (identitySchemaInterpreter == null) {
      throw new DecodeException(
          String.format(
              "No Ethereum record interpreter found for identity scheme %s", nodeIdentity));
    }

    return NodeRecord.fromRawFieldsStrict(
        identitySchemaInterpreter,
        UInt64.fromBytes(RlpUtil.asString(rlpList.get(1), RlpUtil.CONS_UINT64)),
        RlpUtil.asString(rlpList.get(0), RlpUtil.CONS_ANY),
        rlpList.subList(2, rlpList.size()));
  }

  public NodeRecord fromBytes(byte[] bytes) {
    // record    = [signature, seq, k, v, ...]
    return fromRlpList(RlpUtil.decodeSingleList(Bytes.wrap(bytes)));
  }
}
