package org.ethereum.beacon.discovery.schema;

import org.apache.tuweni.bytes.Bytes;

public class WhoAreYouIdentitySchemaInterpreter implements IdentitySchemaInterpreter {

  public static final String NODE_ID_FIELD = "nodeId";

  @Override
  public void verify(final NodeRecord nodeRecord) {
    IdentitySchemaInterpreter.super.verify(nodeRecord);
    if (nodeRecord.get(NODE_ID_FIELD) == null) {
      throw new RuntimeException(
          String.format(
              "Field %s not exists but required for scheme %s", NODE_ID_FIELD, getScheme()));
    }
  }

  @Override
  public IdentitySchema getScheme() {
    return IdentitySchema.WHO_ARE_YOU;
  }

  @Override
  public void sign(final NodeRecord nodeRecord, final Object signOptions) {}

  @Override
  public Bytes getNodeId(final NodeRecord nodeRecord) {
    return (Bytes) nodeRecord.get(NODE_ID_FIELD);
  }
}
