package org.ethereum.beacon.discovery;

import static org.ethereum.beacon.discovery.util.Functions.PRIVKEY_SIZE;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.storage.BucketStats;
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;
import org.web3j.crypto.ECKeyPair;

public class Playground {
  public static void main(String[] args) {
    ECKeyPair keyPair = Functions.generateECKeyPair(new Random(1));
    final Bytes privateKey =
        Bytes.wrap(Utils.extractBytesFromUnsignedBigInt(keyPair.getPrivateKey(), PRIVKEY_SIZE));

    final NodeRecord efBootnode =
        NodeRecordFactory.DEFAULT.fromEnr(
            "enr:-Ku4QHqVeJ8PPICcWk1vSn_XcSkjOkNiTg6Fmii5j6vUQgvzMc9L1goFnLKgXqBJspJjIsB91LTOleFmyWWrFVATGngBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhAMRHkWJc2VjcDI1NmsxoQKLVXFOhp2uX6jeT0DvvDpPcU8FWMjQdR4wMuORMhpX24N1ZHCCIyg");
    final NodeRecord bootnode1 =
        NodeRecordFactory.DEFAULT.fromEnr(
            "enr:-KG4QJRlj4pHagfNIm-Fsx9EVjW4rviuZYzle3tyddm2KAWMJBDGAhxfM2g-pDaaiwE8q19uvLSH4jyvWjypLMr3TIcEhGV0aDKQ9aX9QgAAAAD__________4JpZIJ2NIJpcIQDE8KdiXNlY3AyNTZrMaEDhpehBDbZjM_L9ek699Y7vhUJ-eAdMyQW_Fil522Y0fODdGNwgiMog3VkcIIjKA");
    final NodeRecord bootnode2 =
        NodeRecordFactory.DEFAULT.fromEnr(
            "enr:-KG4QL-eqFoHy0cI31THvtZjpYUu_Jdw_MO7skQRJxY1g5HTN1A0epPCU6vi0gLGUgrzpU-ygeMSS8ewVxDpKfYmxMMGhGV0aDKQtTA_KgAAAAD__________4JpZIJ2NIJpcIQ2_DUbiXNlY3AyNTZrMaED8GJ2vzUqgL6-KD1xalo1CsmY4X1HaDnyl6Y_WayCo9GDdGNwgiMog3VkcIIjKA");
    final NodeRecord prsymBootnode =
        NodeRecordFactory.DEFAULT.fromEnr(
            "enr:-Ku4QImhMc1z8yCiNJ1TyUxdcfNucje3BGwEHzodEZUan8PherEo4sF7pPHPSIB1NNuSg5fZy7qFsjmUKs2ea1Whi0EBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpD1pf1CAAAAAP__________gmlkgnY0gmlwhBLf22SJc2VjcDI1NmsxoQOVphkDqal4QzPMksc5wnpuC3gvSC8AfbFOnZY_On34wIN1ZHCCIyg");

    //    final NodeRecord localNode =
    //        NodeRecordFactory.DEFAULT.fromEnr(
    //
    // "enr:-KG4QCnFchDQi4yh3VU52rPXT4CLlxkA5XVVDkXLQiCVVXzhAYHVNMitutrIZvLrL67QKzXf7pV5qarTXaUoBB20784QhGV0aDKQ9aX9QgAAAAD__________4JpZIJ2NIJpcIR_AAABiXNlY3AyNTZrMaED5bn8vOI3CAHLJdTzVYkTAl8aQSsMHqSYn7VYPmJlG8yDdGNwgiMtg3VkcIIjLQ");
    final NodeRecord node = efBootnode;


    final DiscoverySystem system =
        new DiscoverySystemBuilder()
            .listen("0.0.0.0", 9000)
            .privateKey(privateKey)
            .localNodeRecord(
                new NodeRecordBuilder()
                    .privateKey(privateKey)
                    .address("180.150.110.29", 9000)
                    .seq(0)
                    .build())
            .newAddressHandler(
                (oldRecord, proposedRecord) -> {
                  System.out.println("Proposing address: " + proposedRecord);
                  return Optional.of(proposedRecord);
                })
            .bootnodes(efBootnode, bootnode1, bootnode2, prsymBootnode)
            .build();

    system.start().join();

    system.ping(node).join();
    system.ping(bootnode1).join();
    system.ping(bootnode2).join();

    //    while (true) {
    //      System.out.println(
    //          "Live node count: "
    //              + system
    //                  .streamKnownNodes()
    //                  .filter(record -> record.getStatus() == NodeStatus.ACTIVE)
    //                  .count());
    //      system.reportBucketNodeCount();
    //      LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
    //    }
    System.out.println("Pinged node " + node.getNodeId());
    //    final List<Integer> all = new ArrayList<>();
    for (int i = 0; i <= 258; i++) {
      System.out.println("------------ Distance " + i);
      //      system.findNodes(node, List.of(i)).join();
      //      all.add(i);
    }
    System.out.println("------------ All");
    //    system.findNodes(node, all).join();

    //    system.streamKnownNodes().forEach(record -> system.ping(record.getNode()));

    while (true) {
      final BucketStats stats = system.getBucketStats();
      System.out.println(stats.format());
      system.searchForNewPeers();
      LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(5));
    }
    //    System.exit(0);
  }
}
