/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.schema;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.net.InetSocketAddress;
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
import org.apache.tuweni.crypto.SECP256K1.SecretKey;
import org.apache.tuweni.rlp.RLP;
import org.apache.tuweni.rlp.RLPWriter;
import org.apache.tuweni.units.bigints.UInt64;

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

  private static final EnrFieldInterpreter ENR_FIELD_INTERPRETER = EnrFieldInterpreterV4.DEFAULT;
  private final UInt64 seq;
  // Signature
  private Bytes signature;
  // optional fields
  private final Map<String, Object> fields = new HashMap<>();
  private final IdentitySchemaInterpreter identitySchemaInterpreter;

  private NodeRecord(
      IdentitySchemaInterpreter identitySchemaInterpreter,
      UInt64 seq,
      Bytes signature,
      Map<String, Object> fields) {
    this.seq = seq;
    this.signature = signature;
    this.identitySchemaInterpreter = identitySchemaInterpreter;
    this.fields.putAll(fields);
    // serialise to check size
    Bytes serializedNodeRecord = serialize();
    checkArgument(
        serializedNodeRecord.size() <= MAX_ENCODED_SIZE,
        "Node record exceeds maximum encoded size");
  }

  public static NodeRecord fromValues(
      IdentitySchemaInterpreter identitySchemaInterpreter,
      UInt64 seq,
      List<EnrField> fieldKeyPairs) {
    return new NodeRecord(
        identitySchemaInterpreter,
        seq,
        MutableBytes.create(96),
        fieldKeyPairs.stream().collect(Collectors.toMap(EnrField::getName, EnrField::getValue)));
  }

  public static NodeRecord fromRawFields(
      IdentitySchemaInterpreter identitySchemaInterpreter,
      UInt64 seq,
      Bytes signature,
      Map<String, Object> rawFields) {
    return new NodeRecord(
        identitySchemaInterpreter,
        seq,
        signature,
        rawFields.entrySet().stream()
            .map((e) -> Map.entry(e.getKey(), ENR_FIELD_INTERPRETER.decode(e.getKey(), e.getValue())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
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

  public void sign(final SecretKey secretKey) {
    identitySchemaInterpreter.sign(this, secretKey);
  }

  public void writeRlp(final RLPWriter writer) {
    writeRlp(writer, true);
  }

  public void writeRlp(final RLPWriter writer, final boolean includeSignature) {
    Preconditions.checkNotNull(getSeq(), "Missing sequence number");
    // content   = [seq, k, v, ...]
    // signature = sign(content)
    // record    = [signature, seq, k, v, ...]
    List<String> keySortedList = fields.keySet().stream().sorted().collect(Collectors.toList());
    writeRlp(writer, includeSignature, keySortedList);
  }

  @VisibleForTesting
  void writeRlp(
      final RLPWriter writer, final boolean includeSignature, final List<String> keySortedList) {
    writer.writeList(
        listWriter -> {
          if (includeSignature) {
            listWriter.writeValue(getSignature());
          }
          listWriter.writeBigInteger(getSeq().toBigInteger());

          for (String key : keySortedList) {
            if (fields.get(key) == null) {
              continue;
            }
            listWriter.writeString(key);
            ENR_FIELD_INTERPRETER.encode(listWriter, key, fields.get(key));
          }
        });
  }

  private Bytes asRlpImpl(boolean withSignature) {
    return RLP.encode(writer -> writeRlp(writer, withSignature));
  }

  public Bytes serialize() {
    return serializeImpl(true);
  }

  public Bytes serializeNoSignature() {
    return serializeImpl(false);
  }

  private Bytes serializeImpl(boolean withSignature) {
    return asRlpImpl(withSignature);
  }

  public Bytes getNodeId() {
    return identitySchemaInterpreter.getNodeId(this);
  }

  public Optional<InetSocketAddress> getTcpAddress() {
    return identitySchemaInterpreter.getTcpAddress(this);
  }

  public Optional<InetSocketAddress> getTcp6Address() {
    return identitySchemaInterpreter.getTcp6Address(this);
  }

  public Optional<InetSocketAddress> getUdpAddress() {
    return identitySchemaInterpreter.getUdpAddress(this);
  }

  public Optional<InetSocketAddress> getUdp6Address() {
    return identitySchemaInterpreter.getUdp6Address(this);
  }

  public NodeRecord withNewAddress(
      final InetSocketAddress newUdpAddress,
      final Optional<Integer> newTcpPort,
      final SecretKey secretKey) {
    return identitySchemaInterpreter.createWithNewAddress(
        this, newUdpAddress, newTcpPort, secretKey);
  }

  public NodeRecord withUpdatedCustomField(
      final String fieldName, final Bytes value, final SecretKey secretKey) {
    return identitySchemaInterpreter.createWithUpdatedCustomField(
        this, fieldName, value, secretKey);
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
        + ", udp6Address="
        + getUdp6Address()
        + ", tcp6Address="
        + getTcp6Address()
        + ", asBase64="
        + this.asBase64()
        + ", nodeId="
        + this.getNodeId()
        + ", customFields="
        + fields
        + '}';
  }
}
