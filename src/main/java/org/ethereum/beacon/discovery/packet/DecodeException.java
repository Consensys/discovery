package org.ethereum.beacon.discovery.packet;

public class DecodeException extends RuntimeException {

  public DecodeException(String message) {
    super(message);
  }

  public DecodeException(String message, Throwable cause) {
    super(message, cause);
  }
}
