package org.ethereum.beacon.discovery.message;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt64;

public interface PongData {

    UInt64 getEnrSeq();

    Bytes getRecipientIp();

    int getRecipientPort();

}
