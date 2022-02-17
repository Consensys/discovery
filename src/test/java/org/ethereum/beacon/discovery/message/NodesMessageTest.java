/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.schema.NodeRecordFactory;
import org.junit.jupiter.api.Test;

class NodesMessageTest {
  private final DiscoveryV5MessageDecoder decoder =
      new DiscoveryV5MessageDecoder(NodeRecordFactory.DEFAULT);

  @Test
  void shouldRoundTrip() {
    final NodesMessage original =
        new NodesMessage(
            Bytes.fromHexString("0x85482293"),
            8,
            List.of(
                NodeRecordFactory.DEFAULT.fromEnr(
                    "enr:-KK4QH0RsNJmIG0EX9LSnVxMvg-CAOr3ZFF92hunU63uE7wcYBjG1cFbUTvEa5G_4nDJkRhUq9q2ck9xY-VX1RtBsruBtIRldGgykIL0pysBABAg__________-CaWSCdjSCaXCEEnXQ0YlzZWNwMjU2azGhA1grTzOdMgBvjNrk-vqWtTZsYQIi0QawrhoZrsn5Hd56g3RjcIIjKIN1ZHCCIyg"),
                NodeRecordFactory.DEFAULT.fromEnr(
                    "enr:-LK4QH1xnjotgXwg25IDPjrqRGFnH1ScgNHA3dv1Z8xHCp4uP3N3Jjl_aYv_WIxQRdwZvSukzbwspXZ7JjpldyeVDzMCh2F0dG5ldHOIAAAAAAAAAACEZXRoMpB53wQoAAAQIP__________gmlkgnY0gmlwhIe1te-Jc2VjcDI1NmsxoQOkcGXqbCJYbcClZ3z5f6NWhX_1YPFRYRRWQpJjwSHpVIN0Y3CCIyiDdWRwgiMo")));

    final Bytes rlp = original.getBytes();
    final Message result = decoder.decode(rlp);
    assertThat(result).isEqualTo(original);
    assertThat(result.getBytes()).isEqualTo(rlp);
  }
}
