package zanzibar.huynhvy.namespace.exception;

import zanzibar.huynhvy.shared.exception.ZanzibarException;

/** Thrown when a requested namespace (or version) has no stored config. */
public class NamespaceNotFoundException extends ZanzibarException {

  public NamespaceNotFoundException(String namespace) {
    super("No config found for namespace '%s'".formatted(namespace));
  }

  public NamespaceNotFoundException(String namespace, int version) {
    super("No config found for namespace '%s' version %d".formatted(namespace, version));
  }
}
