/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.ethereum.beacon.discovery.util.RlpUtil.checkComplete;
import static org.ethereum.beacon.discovery.util.RlpUtil.checkMaxSize;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.rlp.RLP;
import org.apache.tuweni.rlp.RLPReader;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.util.DecodeException;
import org.ethereum.beacon.discovery.util.RlpUtil;

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

  public NodeRecord fromRlp(final RLPReader reader) {
    return reader.readList(
        listReader -> {
          final Bytes signature = listReader.readValue();
          final UInt64 seq =
              UInt64.fromBytes(checkMaxSize(listReader.readValue(), RlpUtil.UINT64_MAX_SIZE));

          final Map<String, Object> rawFields = new HashMap<>();
          IdentitySchema nodeIdentity = null;
          String previousKey = null;
          while (!listReader.isComplete()) {
            String key =
                new String(
                    checkMaxSize(listReader.readValue(), MAX_FIELD_KEY_SIZE).toArrayUnsafe(),
                    UTF_8);
            if (previousKey != null && key.compareTo(previousKey) <= 0) {
              throw new DecodeException("ENR fields are not in strict order");
            }
            previousKey = key;

            if ("id".equals(key)) {
              Bytes idVersion = checkMaxSize(listReader.readValue(), MAX_ENR_RLP_SIZE);
              String verString = new String(idVersion.toArrayUnsafe(), UTF_8);
              nodeIdentity = IdentitySchema.fromString(verString);
              if (nodeIdentity == null) { // no interpreter for such id
                throw new DecodeException(
                    String.format(
                        "Unknown node identity scheme '%s', couldn't create node record.",
                        verString));
              }
              rawFields.put(key, idVersion);
            } else {
              rawFields.put(key, readKeyValue(listReader, RlpUtil.MAX_NESTED_LIST_LEVELS));
            }
          }
          if (nodeIdentity == null) { // no `id` key-values
            throw new DecodeException("Unknown node identity scheme, not defined in record ");
          }

          IdentitySchemaInterpreter identitySchemaInterpreter = interpreters.get(nodeIdentity);
          if (identitySchemaInterpreter == null) {
            throw new DecodeException(
                String.format(
                    "No Ethereum record interpreter found for identity scheme %s", nodeIdentity));
          }

          checkComplete(listReader);
          return NodeRecord.fromRawFields(identitySchemaInterpreter, seq, signature, rawFields);
        });
  }

  private Object readKeyValue(final RLPReader reader, final int remainingListLevels) {
    if (reader.nextIsList() && remainingListLevels > 0) {
      return reader.readListContents(
          listReader -> readKeyValue(listReader, remainingListLevels - 1));
    }
    return reader.readValue();
  }

  public NodeRecord fromBytes(byte[] bytes) {
    // record    = [signature, seq, k, v, ...]
    return RLP.decode(Bytes.wrap(bytes), this::fromRlp);
  }
}
