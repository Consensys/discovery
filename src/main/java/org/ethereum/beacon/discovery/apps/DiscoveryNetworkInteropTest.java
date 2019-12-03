/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.apps;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;
import org.ethereum.beacon.discovery.DiscoveryManagerImpl;
import org.ethereum.beacon.discovery.database.Database;
import org.ethereum.beacon.discovery.format.SerializerFactory;
import org.ethereum.beacon.discovery.scheduler.Schedulers;
import org.ethereum.beacon.discovery.schema.EnrField;
import org.ethereum.beacon.discovery.schema.EnrFieldV4;
import org.ethereum.beacon.discovery.schema.IdentitySchema;
import org.ethereum.beacon.discovery.schema.IdentitySchemaV4Interpreter;
import org.ethereum.beacon.discovery.schema.NodeRecord;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.ethereum.beacon.discovery.storage.NodeBucketStorage;
import org.ethereum.beacon.discovery.storage.NodeSerializerFactory;
import org.ethereum.beacon.discovery.storage.NodeTableStorage;
import org.ethereum.beacon.discovery.storage.NodeTableStorageFactoryImpl;
import org.ethereum.beacon.discovery.util.Functions;
import org.javatuples.Pair;

@SuppressWarnings({"DoubleBraceInitialization"})
public class DiscoveryNetworkInteropTest {

  public static void main(String[] args) throws Exception {
    DiscoveryNetworkInteropTest t = new DiscoveryNetworkInteropTest();
    t.testLighthouseInterop();
  }

  static final int SEED = 123456789;

  public static final NodeRecordFactory NODE_RECORD_FACTORY =
      new NodeRecordFactory(new IdentitySchemaV4Interpreter());
  public static final SerializerFactory TEST_SERIALIZER =
      new NodeSerializerFactory(NODE_RECORD_FACTORY);


  @SuppressWarnings({"unchecked", "rawtypes"})
  public void testLighthouseInterop() throws Exception {
    //    final String remoteHostEnr =
    // "-IS4QJBOCmTBOuIE0_z16nV8P1KOyVVIu1gq2S83H5HBmfFaFuevJT0XyKH35LNVxHK5dotDTwqlc9NiRXosBcQ1bJ8BgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCIyk";
    final String remoteHostEnr =
        "-IS4QM5MNwCeleR3p5I1JUvw9JY4xdarw0NYdthidFLjQkR8OmMZe69EaN3brdfSsUsOXoKsaovFr8U71Nw1y_80OfABgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQPKY0yuDUmstAHYpMa2_oxVtw0RW_QAdpzBQA8yWM0xOIN1ZHCCIy0";
    //
    // -IS4QOrJvO6_CDyN0dwE9R8NzUR9CK4v0t_Q6l8EKhMhGhCpKXLMQNYUXbMYN-j6kPjAczrQ1uAwWXAI8PjMGXsJxRMBgmlkgnY0gmlwhH8AAAGJc2VjcDI1NmsxoQJI4MROfzgMfjN1ANb-9fNXFT3xnjzK5NEfNLG4oiMPDoN1ZHCCIy0

    // NODE_RECORD_FACTORY_NO_VERIFICATION.fromBase64(remoteHostEnr);
    NodeRecord remoteNodeRecord = NodeRecordFactory.DEFAULT.fromBase64(remoteHostEnr);
    //    NodeRecord remoteNodeRecord =
    // NODE_RECORD_FACTORY_NO_VERIFICATION.fromBase64(remoteHostEnr);
    remoteNodeRecord.verify();
    System.out.println("remoteEnr:" + remoteNodeRecord.asBase64());
    System.out.println("remoteNodeId:" + remoteNodeRecord.getNodeId());
    System.out.println("remoteNodeRecord:" + remoteNodeRecord);

    Pair<NodeRecord, byte[]> localNodeInfo = createLocalNodeRecord(9002);
    NodeRecord localNodeRecord = localNodeInfo.getValue0();
    System.out.println("localNodeEnr:" + localNodeRecord.asBase64());
    System.out.println("localNodeId:" + localNodeRecord.getNodeId());
    System.out.println("localNodeRecord:" + localNodeRecord);

    byte[] localPrivKey = localNodeInfo.getValue1();

    Database database0 = Database.inMemoryDB();
    NodeTableStorageFactoryImpl nodeTableStorageFactory = new NodeTableStorageFactoryImpl();
    NodeTableStorage nodeTableStorage0 =
        nodeTableStorageFactory.createTable(
            database0,
            TEST_SERIALIZER,
            (oldSeq) -> localNodeRecord,
            () ->
                new ArrayList<>() {
                  {
                    add(remoteNodeRecord);
                  }
                });

    NodeBucketStorage nodeBucketStorage0 =
        nodeTableStorageFactory.createBucketStorage(database0, TEST_SERIALIZER, localNodeRecord);

    DiscoveryManagerImpl discoveryManager0 =
        new DiscoveryManagerImpl(
            nodeTableStorage0.get(),
            nodeBucketStorage0,
            localNodeRecord,
            Bytes.wrap(localPrivKey),
            NODE_RECORD_FACTORY,
            Schedulers.createDefault().newSingleThreadDaemon("server-1"),
            Schedulers.createDefault().newSingleThreadDaemon("client-1"));

    //    CountDownLatch randomSent1to2 = new CountDownLatch(1);
    //    CountDownLatch whoareyouSent2to1 = new CountDownLatch(1);
    //    CountDownLatch authPacketSent1to2 = new CountDownLatch(1);
    //    CountDownLatch nodesSent2to1 = new CountDownLatch(1);

    //    Flux.from(discoveryManager0.getOutgoingMessages())
    //        .map(p -> new UnknownPacket(p.getPacket().getBytes()))
    //        .subscribe(
    //            networkPacket -> {
    //              // 1 -> 2 random
    //              if (randomSent1to2.getCount() != 0) {
    //                RandomPacket randomPacket = networkPacket.getRandomPacket();
    //                System.out.println("1 => 2: " + randomPacket);
    //                randomSent1to2.countDown();
    //              } else if (authPacketSent1to2.getCount() != 0) {
    //                // 1 -> 2 auth packet with FINDNODES
    //                AuthHeaderMessagePacket authHeaderMessagePacket =
    //                    networkPacket.getAuthHeaderMessagePacket();
    //                System.out.println("1 => 2: " + authHeaderMessagePacket);
    //                authPacketSent1to2.countDown();
    //              } else {
    //                throw new RuntimeException("Not expected!");
    //              }
    //            });

    //    Flux.from(discoveryManager0.getOutgoingMessages())
    //        .map(p -> new UnknownPacket(p.getPacket().getBytes()))
    //        .subscribe(
    //            networkPacket -> {
    //              // 1 -> 2 random
    //              if (randomSent1to2.getCount() != 0) {
    //                RandomPacket randomPacket = networkPacket.getRandomPacket();
    //                System.out.println("1 => 2: " + randomPacket);
    //                randomSent1to2.countDown();
    //              } else if (authPacketSent1to2.getCount() != 0) {
    //                // 1 -> 2 auth packet with FINDNODES
    //                AuthHeaderMessagePacket authHeaderMessagePacket =
    //                    networkPacket.getAuthHeaderMessagePacket();
    //                System.out.println("1 => 2: " + authHeaderMessagePacket);
    //                authPacketSent1to2.countDown();
    //              }
    //
    //              // 2 -> 1 whoareyou
    //              else if (whoareyouSent2to1.getCount() != 0) {
    //                WhoAreYouPacket whoAreYouPacket = networkPacket.getWhoAreYouPacket();
    //                System.out.println("2 => 1: " + whoAreYouPacket);
    //                whoareyouSent2to1.countDown();
    //              } else {
    //                // 2 -> 1 nodes
    //                MessagePacket messagePacket = networkPacket.getMessagePacket();
    //                System.out.println("2 => 1: " + messagePacket);
    //                nodesSent2to1.countDown();
    //              }
    //            });

    discoveryManager0.start();

    //    discoveryManager0.findNodes(remoteNodeRecord, 0);

    for (int i = 0; i < 5; i++) {
      //    while (true) {
      Thread.sleep(5000);
      discoveryManager0.ping(remoteNodeRecord);
    }
  }

  Random rnd = new Random(SEED);

  @SuppressWarnings({"unchecked", "rawtypes"})
  public Pair<NodeRecord, byte[]> createLocalNodeRecord(int port) {

    try {
      // set local service node
      byte[] privKey1 = new byte[32];
      rnd.nextBytes(privKey1);
      //      ECKeyPair keyPair1 = ECKeyPair.create(privKey1);

      //      org.apache.milagro.amcl.SECP256K1.ECP ecp =
      //          ECP.fromBytes(keyPair1.getPublicKey().toByteArray());

      //      byte[] pubbytes = new byte[33];
      //      ecp.toBytes(pubbytes, true);

      Bytes localAddressBytes = Bytes.wrap(InetAddress.getByName("127.0.0.1").getAddress());
      Bytes localIp1 =
          Bytes.concatenate(Bytes.wrap(new byte[4 - localAddressBytes.size()]), localAddressBytes);
      NodeRecord nodeRecord1 =
          NodeRecordFactory.DEFAULT.createFromValues(
              //          NODE_RECORD_FACTORY_NO_VERIFICATION.createFromValues(
              UInt64.ZERO,
              Bytes.EMPTY,
              Pair.with(EnrField.ID, IdentitySchema.V4),
              Pair.with(EnrField.IP_V4, localIp1),
              Pair.with(
                  EnrFieldV4.PKEY_SECP256K1,
                  Functions.derivePublicKeyFromPrivate(Bytes.wrap(privKey1))),
              //
              // Bytes.wrap(extractBytesFromUnsignedBigInt(keyPair1.getPublicKey()))),
              Pair.with(EnrField.TCP_V4, port),
              Pair.with(EnrField.UDP_V4, port));
      //      Bytes signature1 = Functions.sign(Bytes.wrap(privKey1),
      // nodeRecord1.serializeNoSignature());
      //      nodeRecord1.setSignature(signature1);
      nodeRecord1.sign(Bytes.wrap(privKey1));
      nodeRecord1.verify();
      return new Pair(nodeRecord1, privKey1);
    } catch (Exception e) {
//      e.printStackTrace();
    }
    return null;
  }

}
