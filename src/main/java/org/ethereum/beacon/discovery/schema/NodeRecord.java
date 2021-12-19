/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.web3j.rlp.RlpEncoder;
import org.web3j.rlp.RlpList;
import org.web3j.rlp.RlpString;
import org.web3j.rlp.RlpType;

/**
 * Ethereum Node Record V4
 *
 * <p>Node record as described in <a href="https://eips.ethereum.org/EIPS/eip-778">EIP-778</a>
 */
public class NodeRecord {

  /**
   * The canonical encoding of a node record is an RLP list of [signature, seq, k, v, ...]. The
   * maximum encoded size of a node record is 300 bytes. Implementations should reject records
   * larger than this size.
   */
  public static final int MAX_ENCODED_SIZE = 300;

  private static final EnrFieldInterpreter enrFieldInterpreter = EnrFieldInterpreterV4.DEFAULT;
  private final UInt64 seq;
  // Signature
  private Bytes signature;
  // optional fields
  private final Map<String, Object> fields = new HashMap<>();
  private final IdentitySchemaInterpreter identitySchemaInterpreter;
  private final Supplier<Bytes> nodeIdCache = Suppliers.memoize(this::calcNodeId);

  private NodeRecord(
      IdentitySchemaInterpreter identitySchemaInterpreter, UInt64 seq, Bytes signature) {
    this.seq = seq;
    this.signature = signature;
    this.identitySchemaInterpreter = identitySchemaInterpreter;
  }

  private NodeRecord(IdentitySchemaInterpreter identitySchemaInterpreter, UInt64 seq) {
    this.seq = seq;
    this.signature = MutableBytes.create(96);
    this.identitySchemaInterpreter = identitySchemaInterpreter;
  }

  public static NodeRecord fromValues(
      IdentitySchemaInterpreter identitySchemaInterpreter,
      UInt64 seq,
      List<EnrField> fieldKeyPairs) {
    NodeRecord nodeRecord = new NodeRecord(identitySchemaInterpreter, seq);
    fieldKeyPairs.forEach(objects -> nodeRecord.set(objects.getName(), objects.getValue()));
    return nodeRecord;
  }

  @SuppressWarnings({"unchecked", "DefaultCharset"})
  public static NodeRecord fromRawFields(
      IdentitySchemaInterpreter identitySchemaInterpreter,
      UInt64 seq,
      Bytes signature,
      List<RlpType> rawFields) {
    NodeRecord nodeRecord = new NodeRecord(identitySchemaInterpreter, seq, signature);
    for (int i = 0; i < rawFields.size(); i += 2) {
      String key = new String(((RlpString) rawFields.get(i)).getBytes());
      nodeRecord.set(key, enrFieldInterpreter.decode(key, rawFields.get(i + 1)));
    }
    return nodeRecord;
  }

  public String asBase64() {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(serialize().toArray());
  }

  public String asEnr() {
    return "enr:" + asBase64();
  }

  public IdentitySchema getIdentityScheme() {
    return identitySchemaInterpreter.getScheme();
  }

  public void set(String key, Object value) {
    fields.put(key, value);
  }

  public Object get(String key) {
    return fields.get(key);
  }

  public void forEachField(BiConsumer<String, Object> consumer) {
    fields.forEach(consumer);
  }

  public boolean containsKey(String key) {
    return fields.containsKey(key);
  }

  public UInt64 getSeq() {
    return seq;
  }

  public Bytes getSignature() {
    return signature;
  }

  public void setSignature(Bytes signature) {
    this.signature = signature;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NodeRecord that = (NodeRecord) o;
    return Objects.equals(seq, that.seq)
        && Objects.equals(signature, that.signature)
        && Objects.equals(fields, that.fields);
  }

  @Override
  public int hashCode() {
    return Objects.hash(seq, signature, fields);
  }

  public boolean isValid() {
    return identitySchemaInterpreter.isValid(this);
  }

  public void sign(Bytes privateKey) {
    identitySchemaInterpreter.sign(this, privateKey);
  }

  public RlpList asRlp() {
    return asRlpImpl(true);
  }

  public RlpList asRlpNoSignature() {
    return asRlpImpl(false);
  }

  private RlpList asRlpImpl(boolean withSignature) {
    Preconditions.checkNotNull(getSeq(), "Missing sequence number");
    // content   = [seq, k, v, ...]
    // signature = sign(content)
    // record    = [signature, seq, k, v, ...]
    List<RlpType> values = new ArrayList<>();
    if (withSignature) {
      values.add(RlpString.create(getSignature().toArray()));
    }
    values.add(RlpString.create(getSeq().toBigInteger()));
    List<String> keySortedList = fields.keySet().stream().sorted().collect(Collectors.toList());
    for (String key : keySortedList) {
      if (fields.get(key) == null) {
        continue;
      }
      values.add(RlpString.create(key));
      values.add(enrFieldInterpreter.encode(key, fields.get(key)));
    }

    return new RlpList(values);
  }

  public Bytes serialize() {
    return serializeImpl(true);
  }

  public Bytes serializeNoSignature() {
    return serializeImpl(false);
  }

  private Bytes serializeImpl(boolean withSignature) {
    RlpType rlpRecord = withSignature ? asRlp() : asRlpNoSignature();
    byte[] bytes = RlpEncoder.encode(rlpRecord);
    Preconditions.checkArgument(
        bytes.length <= MAX_ENCODED_SIZE, "Node record exceeds maximum encoded size");
    return Bytes.wrap(bytes);
  }

  public Bytes getNodeId() {
    return nodeIdCache.get();
  }

  private Bytes calcNodeId() {
    return identitySchemaInterpreter.getNodeId(this);
  }

  public Optional<InetSocketAddress> getTcpAddress() {
    return identitySchemaInterpreter.getTcpAddress(this);
  }

  public Optional<InetSocketAddress> getUdpAddress() {
    return identitySchemaInterpreter.getUdpAddress(this);
  }

  public NodeRecord withNewAddress(
      final InetSocketAddress newUdpAddress,
      final Optional<Integer> newTcpPort,
      final Bytes privateKey) {
    return identitySchemaInterpreter.createWithNewAddress(
        this, newUdpAddress, newTcpPort, privateKey);
  }

  public NodeRecord withUpdatedCustomField(
      final String fieldName, final Bytes value, final Bytes privateKey) {
    return identitySchemaInterpreter.createWithUpdatedCustomField(
        this, fieldName, value, privateKey);
  }

  @Override
  public String toString() {
    return "NodeRecord{"
        + "seq="
        + seq
        + ", publicKey="
        + fields.get(EnrField.PKEY_SECP256K1)
        + ", udpAddress="
        + getUdpAddress()
        + ", tcpAddress="
        + getTcpAddress()
        + ", asBase64="
        + this.asBase64()
        + ", nodeId="
        + this.getNodeId()
        + ", customFields="
        + fields
        + '}';
  }
}
