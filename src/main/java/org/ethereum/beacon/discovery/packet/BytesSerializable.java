package org.ethereum.beacon.discovery.packet;

import org.apache.tuweni.bytes.Bytes;
import org.ethereum.beacon.discovery.util.DecodeException;

public interface BytesSerializable {

  void validate() throws DecodeException;

  Bytes getBytes();
}
