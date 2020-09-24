package org.ethereum.beacon.discovery.util;

import java.util.function.Supplier;

public class DecodeException extends RuntimeException {
  public interface RunnableEx {
    void run() throws Exception;
  }

  public static void wrap(RunnableEx r) throws DecodeException {
    wrap(() -> "Error decoding", r);
  }
  public static void wrap(Supplier<String> err, RunnableEx r) throws DecodeException {
    try {
      r.run();
    } catch (DecodeException e) {
      throw e;
    } catch (Exception e) {
      throw new DecodeException(err.get(), e);
    }
  }

  public DecodeException(String message) {
    super(message);
  }

  public DecodeException(String message, Throwable cause) {
    super(message, cause);
  }
}
