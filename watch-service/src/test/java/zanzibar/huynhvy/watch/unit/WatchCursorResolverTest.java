package zanzibar.huynhvy.watch.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.shared.security.ZookieValidator;
import zanzibar.huynhvy.watch.stream.CursorOutOfRangeException;
import zanzibar.huynhvy.watch.stream.WatchCursor;
import zanzibar.huynhvy.watch.stream.WatchCursorResolver;

class WatchCursorResolverTest {

  private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
  private static final Duration RETENTION = Duration.ofHours(24);

  private final ZookieValidator zookieValidator = mock(ZookieValidator.class);
  private final WatchCursorResolver resolver =
      new WatchCursorResolver(zookieValidator, RETENTION, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void no_zookie_watches_from_now_on() {
    assertThat(resolver.resolve(null).isLive()).isTrue();
    assertThat(resolver.resolve("").isLive()).isTrue();
    assertThat(resolver.resolve("   ").isLive()).isTrue();
  }

  @Test
  void a_zookie_inside_the_retention_window_resolves_to_its_commit_timestamp() {
    Instant write = NOW.minus(Duration.ofHours(1));
    when(zookieValidator.readTimestampNanos(any())).thenReturn(OptionalLong.of(nanos(write)));

    WatchCursor cursor = resolver.resolve("zk");

    assertThat(cursor.isLive()).isFalse();
    assertThat(cursor.streamOffset()).isEqualTo(Date.from(write));
  }

  @Test
  void an_unverifiable_zookie_is_rejected() {
    when(zookieValidator.readTimestampNanos(any())).thenReturn(OptionalLong.empty());

    assertThatThrownBy(() -> resolver.resolve("tampered"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_zookie_older_than_retention_is_rejected_rather_than_silently_truncated() {
    Instant tooOld = NOW.minus(RETENTION).minus(Duration.ofMinutes(1));
    when(zookieValidator.readTimestampNanos(any())).thenReturn(OptionalLong.of(nanos(tooOld)));

    assertThatThrownBy(() -> resolver.resolve("stale"))
        .isInstanceOf(CursorOutOfRangeException.class);
  }

  @Test
  void a_zookie_just_inside_retention_is_accepted() {
    Instant edge = NOW.minus(RETENTION).plus(Duration.ofMinutes(1));
    when(zookieValidator.readTimestampNanos(any())).thenReturn(OptionalLong.of(nanos(edge)));

    assertThat(resolver.resolve("edge").isLive()).isFalse();
  }

  private static long nanos(Instant instant) {
    return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
  }
}
