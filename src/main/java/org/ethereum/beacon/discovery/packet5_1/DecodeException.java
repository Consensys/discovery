package org.ethereum.beacon.discovery.packet5_1;

public class DecodeException extends RuntimeException {

  public DecodeException(String message) {
    super(message);
  }

  public DecodeException(String message, Throwable cause) {
    super(message, cause);
  }
}
