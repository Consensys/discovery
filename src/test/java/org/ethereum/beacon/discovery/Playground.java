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
import org.ethereum.beacon.discovery.util.Functions;
import org.ethereum.beacon.discovery.util.Utils;
import org.web3j.crypto.ECKeyPair;

public class Playground {
  public static void main(String[] args) {
    final DiscoverySystem server = startServer();

    ECKeyPair keyPair = Functions.generateECKeyPair(new Random(1));
    final Bytes privateKey =
        Bytes.wrap(Utils.extractBytesFromUnsignedBigInt(keyPair.getPrivateKey(), PRIVKEY_SIZE));

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
                  System.out.println("Propsing address: " + proposedRecord);
                  return Optional.of(proposedRecord);
                })
            .build();
    final NodeRecord oonoonba1 =
        NodeRecordFactory.DEFAULT.fromEnr(
            "enr:-KG4QLXhcaTSEqPF-g5T_t-7NJJ6DQTHy8yCV-vvjJHU7jwOUpGMdIcvlKB4roS9qG1mi-P38Pvq1GkHYblRpOvfi6UDhGV0aDKQk_tI4gEASBEKAAAAAAAAAIJpZIJ2NIJpcIQS2MfriXNlY3AyNTZrMaECmgO9ATicNnBAl0Z1wKtbfvVlxv70aiJ7Obx_bFyhGpeDdGNwgiMog3VkcIIjKA");
    //    final NodeRecord efBootnode =
    //        NodeRecordFactory.DEFAULT.fromEnr(
    //
    // "enr:-Ku4QHqVeJ8PPICcWk1vSn_XcSkjOkNiTg6Fmii5j6vUQgvzMc9L1goFnLKgXqBJspJjIsB91LTOleFmyWWrFVATGngBh2F0dG5ldHOIAAAAAAAAAACEZXRoMpC1MD8qAAAAAP__________gmlkgnY0gmlwhAMRHkWJc2VjcDI1NmsxoQKLVXFOhp2uX6jeT0DvvDpPcU8FWMjQdR4wMuORMhpX24N1ZHCCIyg");
    //    final NodeRecord bootnode1 =
    //        NodeRecordFactory.DEFAULT.fromEnr(
    //
    // "enr:-KG4QJRlj4pHagfNIm-Fsx9EVjW4rviuZYzle3tyddm2KAWMJBDGAhxfM2g-pDaaiwE8q19uvLSH4jyvWjypLMr3TIcEhGV0aDKQ9aX9QgAAAAD__________4JpZIJ2NIJpcIQDE8KdiXNlY3AyNTZrMaEDhpehBDbZjM_L9ek699Y7vhUJ-eAdMyQW_Fil522Y0fODdGNwgiMog3VkcIIjKA");
    //    final NodeRecord bootnode2 =
    //        NodeRecordFactory.DEFAULT.fromEnr(
    //
    // "enr:-KG4QL-eqFoHy0cI31THvtZjpYUu_Jdw_MO7skQRJxY1g5HTN1A0epPCU6vi0gLGUgrzpU-ygeMSS8ewVxDpKfYmxMMGhGV0aDKQtTA_KgAAAAD__________4JpZIJ2NIJpcIQ2_DUbiXNlY3AyNTZrMaED8GJ2vzUqgL6-KD1xalo1CsmY4X1HaDnyl6Y_WayCo9GDdGNwgiMog3VkcIIjKA");
    final NodeRecord node = server.getLocalNodeRecord();
    System.out.println("Server enr seq: " + server.getLocalNodeRecord().getSeq());
    server.updateCustomFieldValue("eth2", Bytes.fromHexString("0x1234"));
    System.out.println("Server enr seq: " + server.getLocalNodeRecord().getSeq());
    system.start().join();

    system.ping(node).join();
    System.out.println("Pinged node " + node.getNodeId());
    //    final List<Integer> all = new ArrayList<>();
    //    for (int i = 0; i <= 258; i++) {
    ////      System.out.println("------------ Distance " + i);
    ////      system.findNodes(node, List.of(0)).join();
    //      all.add(i);
    //    }
    //    System.out.println("------------ All");
    //    system.findNodes(node, all).join();

    while (true) {
      system
          .streamKnownNodes()
          .forEach(
              record ->
                  System.out.println(
                      "Known node: " + record.getStatus() + " - " + record.getNode()));
      LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(1));
    }
    //    System.exit(0);
  }

  private static DiscoverySystem startServer() {
    ECKeyPair keyPair = Functions.generateECKeyPair(new Random(1));
    final Bytes privateKey =
        Bytes.wrap(Utils.extractBytesFromUnsignedBigInt(keyPair.getPrivateKey(), PRIVKEY_SIZE));
    final DiscoverySystem serverSystem =
        new DiscoverySystemBuilder()
            .listen("0.0.0.0", 9001)
            .privateKey(privateKey)
            .localNodeRecord(
                new NodeRecordBuilder()
                    .privateKey(privateKey)
                    .address("127.0.0.1", 9001)
                    .seq(0)
                    .customField("eth2", Bytes.fromHexString("0x0000"))
                    .build())
            .newAddressHandler(
                ((oldRecord, proposedRecord) -> {
                  System.out.println("Server proposing address: " + proposedRecord);
                  return Optional.of(proposedRecord);
                }))
            .build();

    serverSystem.start().join();
    return serverSystem;
  }
}
