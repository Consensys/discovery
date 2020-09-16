package org.ethereum.beacon.discovery.packet5_1;

public class PacketDecodeException extends RuntimeException {

  public PacketDecodeException(String message) {
    super(message);
  }

  public PacketDecodeException(String message, Throwable cause) {
    super(message, cause);
  }
}
