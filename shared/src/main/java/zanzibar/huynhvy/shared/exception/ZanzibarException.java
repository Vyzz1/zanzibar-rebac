package zanzibar.huynhvy.shared.exception;

/** Base type for all domain exceptions across the services. */
public abstract class ZanzibarException extends RuntimeException {

  protected ZanzibarException(String message) {
    super(message);
  }

  protected ZanzibarException(String message, Throwable cause) {
    super(message, cause);
  }
}
