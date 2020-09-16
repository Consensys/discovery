package org.ethereum.beacon.discovery.packet5_1;

import org.ethereum.beacon.discovery.type.Bytes12;

public interface OrdinaryMessagePacket extends MessagePacket {

  @Override
  Bytes12 getAuthData();
}
