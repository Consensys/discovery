/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.storage;

import static java.util.stream.Collectors.toCollection;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.apache.tuweni.v2.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.util.Functions;

public class KBucketsIterator implements Iterator<NodeRecord> {

  private final KBuckets buckets;
  private final Comparator<NodeRecord> distanceComparator;
  private int lowDistance;
  private int highDistance;

  private Iterator<NodeRecord> currentBatch = Collections.emptyIterator();

  public KBucketsIterator(
      final KBuckets buckets, final Bytes homeNodeId, final Bytes targetNodeId) {
    this.buckets = buckets;
    this.distanceComparator =
        Comparator.comparing(node -> Functions.distance(targetNodeId, node.getNodeId()));
    final int initialDistance = Functions.logDistance(homeNodeId, targetNodeId);
    lowDistance = initialDistance;
    highDistance = initialDistance;
  }

  @Override
  public boolean hasNext() {
    while (!currentBatch.hasNext() && hasMoreBucketsToScan()) {
      updateCurrentBatch();
      // Prepare for the next buckets
      lowDistance--;
      highDistance++;
    }
    return currentBatch.hasNext();
  }

  private boolean hasMoreBucketsToScan() {
    return lowDistance > 0 || highDistance <= KBuckets.MAXIMUM_BUCKET;
  }

  private void updateCurrentBatch() {
    final Stream<NodeRecord> lowNodes =
        lowDistance > 0 ? buckets.getLiveNodeRecords(lowDistance) : Stream.empty();
    final Stream<NodeRecord> highNodes =
        highDistance > lowDistance && highDistance <= KBuckets.MAXIMUM_BUCKET
            ? buckets.getLiveNodeRecords(highDistance)
            : Stream.empty();
    currentBatch =
        Stream.concat(lowNodes, highNodes)
            .collect(toCollection(() -> new TreeSet<>(distanceComparator)))
            .iterator();
  }

  @Override
  public NodeRecord next() {
    return currentBatch.next();
  }
}
