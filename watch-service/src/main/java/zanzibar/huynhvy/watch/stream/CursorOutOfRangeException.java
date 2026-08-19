package zanzibar.huynhvy.watch.stream;

import zanzibar.huynhvy.shared.exception.ZanzibarException;

/**
 * The client's Zookie points further back than the stream still retains, so the gap cannot be
 * replayed. Reported rather than silently starting from the oldest retained event, which would look
 * like a complete resume while missing changes.
 */
public class CursorOutOfRangeException extends ZanzibarException {

  public CursorOutOfRangeException(String message) {
    super(message);
  }
}
