/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.util.RlpUtil;
import org.ethereum.beacon.discovery.util.Utils;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

public class NodeRecordFactory {
  public static final NodeRecordFactory DEFAULT =
      new NodeRecordFactory(new IdentitySchemaV4Interpreter());
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

  public NodeRecord fromBytes(Bytes bytes) {
    return fromBytes(bytes.toArray());
  }

  @SuppressWarnings({"DefaultCharset"})
  public NodeRecord fromRlpList(RlpList rlpList) {
    List<RlpType> values = rlpList.getValues();
    if (values.size() < 4) {
      throw new RuntimeException(
          String.format("Unable to deserialize ENR with less than 4 fields, [%s]", values));
    }

    // TODO: repair as id is not first now
    IdentitySchema nodeIdentity = null;
    boolean idFound = false;
    for (int i = 2; i < values.size(); i += 2) {
      RlpString id = (RlpString) values.get(i);
      if (!"id".equals(new String(id.getBytes()))) {
        continue;
      }

      RlpString idVersion = (RlpString) values.get(i + 1);
      nodeIdentity = IdentitySchema.fromString(new String(idVersion.getBytes()));
      if (nodeIdentity == null) { // no interpreter for such id
        throw new RuntimeException(
            String.format(
                "Unknown node identity scheme '%s', couldn't create node record.",
                idVersion.asString()));
      }
      idFound = true;
      break;
    }
    if (!idFound) { // no `id` key-values
      throw new RuntimeException("Unknown node identity scheme, not defined in record ");
    }

    IdentitySchemaInterpreter identitySchemaInterpreter = interpreters.get(nodeIdentity);
    if (identitySchemaInterpreter == null) {
      throw new RuntimeException(
          String.format(
              "No Ethereum record interpreter found for identity scheme %s", nodeIdentity));
    }

    return NodeRecord.fromRawFields(
        identitySchemaInterpreter,
        UInt64.fromBytes(Utils.leftPad(Bytes.wrap(((RlpString) values.get(1)).getBytes()), 8)),
        Bytes.wrap(((RlpString) values.get(0)).getBytes()),
        values.subList(2, values.size()));
  }

  public NodeRecord fromBytes(byte[] bytes) {
    // record    = [signature, seq, k, v, ...]
    return fromRlpList(RlpUtil.decodeSingleList(Bytes.wrap(bytes)));
  }
}
