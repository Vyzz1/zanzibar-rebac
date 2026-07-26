package zanzibar.huynhvy.namespace.exception;

import zanzibar.huynhvy.shared.exception.ZanzibarException;

/** Thrown when a submitted namespace config fails validation (bad reference or cycle). */
public class InvalidNamespaceConfigException extends ZanzibarException {

  public InvalidNamespaceConfigException(String message) {
    super(message);
  }
}
