package zanzibar.huynhvy.watch.stream;

import java.time.Clock;
import java.time.Duration;
import java.util.OptionalLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.shared.security.ZookieValidator;

/**
 * Turns the Zookie a Watch client sends into the stream offset to resume from.
 *
 * <p>No Zookie means "from now on". A Zookie is only trusted after its HMAC checks out, and is
 * rejected when it predates the stream's retention window — the events in between are gone, and
 * silently starting later would look like a successful resume while skipping changes.
 */
@Component
public class WatchCursorResolver {

  private final ZookieValidator zookieValidator;
  private final Duration retention;
  private final Clock clock;

  @Autowired
  public WatchCursorResolver(
      ZookieValidator zookieValidator, @Value("${watch.stream.max-age:24h}") Duration retention) {
    this(zookieValidator, retention, Clock.systemUTC());
  }

  /** Explicit clock, so retention expiry can be exercised deterministically. */
  public WatchCursorResolver(ZookieValidator zookieValidator, Duration retention, Clock clock) {
    this.zookieValidator = zookieValidator;
    this.retention = retention;
    this.clock = clock;
  }

  /**
   * @throws IllegalArgumentException if the Zookie is malformed or its HMAC does not match
   * @throws CursorOutOfRangeException if it points before the retained window
   */
  public WatchCursor resolve(String zookie) {
    if (zookie == null || zookie.isBlank()) {
      return WatchCursor.live();
    }

    OptionalLong commitNanos = zookieValidator.readTimestampNanos(new Zookie(zookie));
    if (commitNanos.isEmpty()) {
      throw new IllegalArgumentException("Invalid Zookie");
    }

    long oldestRetainedNanos = millisToNanos(clock.millis()) - retention.toNanos();
    if (commitNanos.getAsLong() < oldestRetainedNanos) {
      throw new CursorOutOfRangeException(
          "Zookie is older than the " + retention + " retention window; cannot replay from it");
    }
    return WatchCursor.from(commitNanos.getAsLong());
  }

  private static long millisToNanos(long millis) {
    return millis * 1_000_000L;
  }
}
