/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ethereum.beacon.discovery.app;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.tuweni.crypto.SECP256K1.KeyPair;
import org.ethereum.beacon.discovery.DiscoverySystem;
import org.ethereum.beacon.discovery.DiscoverySystemBuilder;
import org.ethereum.beacon.discovery.crypto.DefaultSigner;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.util.Functions;

public class DiscoveryTestServer {

  public static void main(String[] args)
      throws InterruptedException, ExecutionException, TimeoutException {
    if (args.length < 2) {
      System.out.println("ERROR: Too few arguments");
      printHelp();
      return;
    }

    String sExternalIP = args[0];
    String sPort = args[1];
    String sPrivateKeySeed = args.length > 2 ? args[2] : null;
    List<String> sBootnodes = new ArrayList<>();
    for (int i = 3; i < args.length; i++) {
      sBootnodes.add(args[i]);
    }

    InetAddress address;
    try {
      address = InetAddress.getByName(sExternalIP);
    } catch (Exception e) {
      System.out.println(
          "ERROR: Couldn't parse <externalIp> argument: '" + sExternalIP + "': " + e);
      printHelp();
      return;
    }

    int port;
    try {
      port = Integer.parseInt(sPort);
    } catch (Exception e) {
      System.out.println("ERROR: Couldn't parse <listenPort> argument: '" + sPort + "': " + e);
      printHelp();
      return;
    }

    int pkSeed;
    try {
      if (sPrivateKeySeed == null) {
        pkSeed = new Random().nextInt();
      } else {
        pkSeed = Integer.parseInt(sPrivateKeySeed);
      }
    } catch (Exception e) {
      System.out.println(
          "ERROR: Couldn't parse <privateKeySeed> argument: '" + sPrivateKeySeed + "': " + e);
      printHelp();
      return;
    }

    List<NodeRecord> bootnodes = new ArrayList<>();
    for (String sBootnode : sBootnodes) {
      try {
        NodeRecord nodeRecord = NodeRecordFactory.DEFAULT.fromBase64(sBootnode);
        bootnodes.add(nodeRecord);
      } catch (Exception e) {
        System.out.println("ERROR: Couldn't parse bootnode ENR: '" + sBootnode + "': " + e);
        printHelp();
        return;
      }
    }

    System.out.println("Starting discovery...");
    final Random rnd = new Random(pkSeed);
    final KeyPair keyPair = Functions.randomKeyPair(rnd);

    final NodeRecord nodeRecord =
        new NodeRecordBuilder()
            .signer(new DefaultSigner(keyPair.secretKey()))
            .address(address.getHostAddress(), port)
            .build();

    DiscoverySystemBuilder discoverySystemBuilder =
        new DiscoverySystemBuilder()
            .listen("0.0.0.0", port)
            .localNodeRecord(nodeRecord)
            .secretKey(keyPair.secretKey())
            .bootnodes(bootnodes);
    final DiscoverySystem discoverySystem = discoverySystemBuilder.build();
    discoverySystem.start().get(5, TimeUnit.SECONDS);

    NodeRecord myNode = discoverySystem.getLocalNodeRecord();
    System.out.println("Discovery started!");
    System.out.println(
        "Home node: "
            + myNode.getUdpAddress().map(InetSocketAddress::toString).orElse("<unknown>")
            + ", nodeId: "
            + myNode.getNodeId());
    System.out.println("Home node ENR: " + myNode.asEnr());

    Set<NodeRecord> activeKnownNodes = new HashSet<>();
    while (true) {
      List<NodeRecord> newActiveNodes =
          discoverySystem
              .streamLiveNodes()
              .filter(r -> !activeKnownNodes.contains(r))
              .collect(Collectors.toList());

      activeKnownNodes.addAll(newActiveNodes);
      newActiveNodes.forEach(
          n -> {
            System.out.println(
                "New active node: "
                    + n.getNodeId()
                    + " @ "
                    + n.getUdpAddress().map(InetSocketAddress::toString).orElse("<unknown>"));
          });
      Thread.sleep(500);
    }
  }

  static void printHelp() {
    System.out.println("DiscoveryTestServer arguments:");
    System.out.println("<externalIp> <listenPort> [privateKeySeed] [bootNode1] [bootNode2] ...");
    System.out.println("Examples:");
    System.out.println("23.44.56.78 9000");
    System.out.println("23.44.56.78 9000 5");
    System.out.println(
        "23.44.56.78 9001 123 -IS4QIrMgVOYuw2mq68f9hFGTlPzJT5pRWIqKTYL93C5xasmfUGUydi2XrjsbxO1MLYGEl1rR5H1iov6gxOyhegW9hYBgmlkgnY0gmlwhLyGRgGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCIyo");
  }
}
