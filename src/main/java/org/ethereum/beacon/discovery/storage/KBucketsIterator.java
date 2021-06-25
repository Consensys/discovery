package org.ethereum.beacon.discovery.storage;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.util.Functions;

public class KBucketsIterator implements Iterator<NodeRecord> {

  private final KBuckets buckets;
  private final Bytes targetNodeId;
  private int lowDistance;
  private int highDistance;

  private Iterator<NodeRecord> currentBatch = Collections.emptyIterator();

  public KBucketsIterator(
      final KBuckets buckets, final Bytes homeNodeId, final Bytes targetNodeId) {
    this.buckets = buckets;
    this.targetNodeId = targetNodeId;
    final int initialDistance = Functions.logDistance(homeNodeId, targetNodeId);
    lowDistance = initialDistance;
    highDistance = initialDistance;
  }

  @Override
  public boolean hasNext() {
    while (!currentBatch.hasNext() && (lowDistance > 1 || highDistance < KBuckets.MAXIMUM_BUCKET)) {
      // Move to the next buckets
      lowDistance--;
      highDistance++;
      currentBatch =
          Stream.concat(
                  buckets.getLiveNodeRecords(lowDistance), buckets.getLiveNodeRecords(highDistance))
              .sorted(
                  Comparator.comparing(
                      node -> Functions.logDistance(targetNodeId, node.getNodeId())))
              .collect(Collectors.toList())
              .iterator();
    }
    return currentBatch.hasNext();
  }

  @Override
  public NodeRecord next() {
    return currentBatch.next();
  }
}
