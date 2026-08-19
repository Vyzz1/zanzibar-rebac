package zanzibar.huynhvy.watch.stream;

import java.util.Date;

/**
 * Where a Watch subscription starts reading the tuple-change stream, expressed as an {@code
 * x-stream-offset} value.
 *
 * <p>{@link #live()} is {@code "next"} — only changes published from now on. {@link #from(long)}
 * resolves a client's Zookie to the moment of the write it already observed, so everything since is
 * replayed before the stream goes live.
 */
public record WatchCursor(Object streamOffset) {

  private static final String LIVE = "next";

  public static WatchCursor live() {
    return new WatchCursor(LIVE);
  }

  /**
   * A cursor at the given commit timestamp. RabbitMQ seeks to the chunk containing it, so replay
   * may begin slightly earlier — consumers must tolerate seeing an event twice.
   */
  public static WatchCursor from(long commitNanos) {
    return new WatchCursor(new Date(commitNanos / 1_000_000L));
  }

  public boolean isLive() {
    return LIVE.equals(streamOffset);
  }
}
