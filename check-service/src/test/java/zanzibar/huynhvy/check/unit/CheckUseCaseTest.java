package zanzibar.huynhvy.check.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.check.cache.CacheKeyStrategy;
import zanzibar.huynhvy.check.cache.CachedCheck;
import zanzibar.huynhvy.check.cache.TupleCache;
import zanzibar.huynhvy.check.domain.CheckUseCase;
import zanzibar.huynhvy.check.domain.GraphTraverser;
import zanzibar.huynhvy.check.domain.NamespaceConfigProvider;
import zanzibar.huynhvy.check.domain.SnapshotClock;
import zanzibar.huynhvy.check.metrics.CheckMetrics;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.security.ZookieValidator;

class CheckUseCaseTest {

  private static final RelationTuple TUPLE =
      new RelationTuple("doc", "report.pdf", "viewer", "user:bob");
  private static final String KEY = "doc:report.pdf:viewer:user:bob";

  private GraphTraverser graphTraverser;
  private ZookieValidator zookieValidator;
  private TupleCache tupleCache;
  private CacheKeyStrategy cacheKeyStrategy;
  private SnapshotClock snapshotClock;
  private SimpleMeterRegistry meterRegistry;
  private CheckMetrics checkMetrics;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    checkMetrics = new CheckMetrics(meterRegistry);
    graphTraverser = mock(GraphTraverser.class);
    zookieValidator = mock(ZookieValidator.class);
    tupleCache = mock(TupleCache.class);
    cacheKeyStrategy = mock(CacheKeyStrategy.class);
    snapshotClock = mock(SnapshotClock.class);
  }

  private CheckUseCase useCase(boolean cacheEnabled) {
    return new CheckUseCase(
        graphTraverser,
        mock(NamespaceConfigProvider.class),
        zookieValidator,
        tupleCache,
        cacheKeyStrategy,
        snapshotClock,
        checkMetrics,
        cacheEnabled,
        60);
  }

  private void keyIs(String key) {
    when(cacheKeyStrategy.key(any())).thenReturn(key);
  }

  private void traverserReturns(boolean allowed) {
    when(graphTraverser.evaluate(eq("doc"), eq("report.pdf"), eq("viewer"), eq("user:bob"), any()))
        .thenReturn(allowed);
  }

  @Test
  void a_miss_evaluates_and_caches_the_result() {
    keyIs(KEY);
    when(tupleCache.get(KEY)).thenReturn(Optional.empty());
    when(snapshotClock.nowNanos()).thenReturn(500L);
    traverserReturns(true);

    assertThat(useCase(true).check(TUPLE, null)).isTrue();

    verify(tupleCache).put(KEY, true, 500L, Duration.ofSeconds(60));
  }

  @Test
  void a_cache_hit_without_a_zookie_is_served_without_evaluating() {
    keyIs(KEY);
    when(tupleCache.get(KEY)).thenReturn(Optional.of(new CachedCheck(true, 100L)));

    assertThat(useCase(true).check(TUPLE, null)).isTrue();

    verify(graphTraverser, never()).evaluate(any(), any(), any(), any(), any());
    verify(tupleCache, never()).put(any(), anyBoolean(), anyLong(), any());
  }

  @Test
  void a_zookie_serves_the_cache_only_when_the_snapshot_is_fresh_enough() {
    keyIs(KEY);
    when(tupleCache.get(KEY)).thenReturn(Optional.of(new CachedCheck(true, 100L)));
    when(zookieValidator.readTimestampNanos(any())).thenReturn(OptionalLong.of(50L)); // 100 >= 50

    assertThat(useCase(true).check(TUPLE, "zk")).isTrue();

    verify(graphTraverser, never()).evaluate(any(), any(), any(), any(), any());
  }

  @Test
  void a_zookie_newer_than_the_snapshot_bypasses_the_stale_cache() {
    keyIs(KEY);
    when(tupleCache.get(KEY)).thenReturn(Optional.of(new CachedCheck(true, 100L)));
    when(snapshotClock.nowNanos()).thenReturn(500L);
    when(zookieValidator.readTimestampNanos(any())).thenReturn(OptionalLong.of(200L)); // 100 < 200
    traverserReturns(false);

    assertThat(useCase(true).check(TUPLE, "zk")).isFalse();

    verify(graphTraverser)
        .evaluate(eq("doc"), eq("report.pdf"), eq("viewer"), eq("user:bob"), any());
    verify(tupleCache).put(KEY, false, 500L, Duration.ofSeconds(60));
  }

  @Test
  void an_invalid_zookie_is_rejected_without_touching_the_cache_or_evaluating() {
    when(zookieValidator.readTimestampNanos(any())).thenReturn(OptionalLong.empty());

    assertThatThrownBy(() -> useCase(true).check(TUPLE, "bad"))
        .isInstanceOf(IllegalArgumentException.class);

    verifyNoInteractions(tupleCache, graphTraverser);
  }

  @Test
  void a_blank_zookie_skips_validation() {
    keyIs(KEY);
    when(tupleCache.get(KEY)).thenReturn(Optional.empty());
    when(snapshotClock.nowNanos()).thenReturn(500L);
    traverserReturns(true);

    useCase(true).check(TUPLE, "");

    verifyNoInteractions(zookieValidator);
  }

  @Test
  void records_a_miss_and_an_allowed_duration() {
    keyIs(KEY);
    when(tupleCache.get(KEY)).thenReturn(Optional.empty());
    when(snapshotClock.nowNanos()).thenReturn(500L);
    traverserReturns(true);

    useCase(true).check(TUPLE, null);

    assertThat(cacheCount("miss")).isEqualTo(1);
    assertThat(cacheCount("hit")).isZero();
    assertThat(
            meterRegistry.get("zanzibar.check.duration").tag("result", "allowed").timer().count())
        .isEqualTo(1);
  }

  @Test
  void records_a_hit_and_a_stale_bypass_separately() {
    keyIs(KEY);
    when(tupleCache.get(KEY)).thenReturn(Optional.of(new CachedCheck(true, 100L)));
    useCase(true).check(TUPLE, null); // no zookie -> hit

    when(zookieValidator.readTimestampNanos(any())).thenReturn(OptionalLong.of(200L)); // 100 < 200
    when(snapshotClock.nowNanos()).thenReturn(500L);
    traverserReturns(true);
    useCase(true).check(TUPLE, "zk"); // cached but too old -> stale

    assertThat(cacheCount("hit")).isEqualTo(1);
    assertThat(cacheCount("stale")).isEqualTo(1);
  }

  private double cacheCount(String result) {
    return meterRegistry.get("zanzibar.check.cache").tag("result", result).counter().count();
  }

  @Test
  void a_disabled_cache_always_evaluates_and_never_caches() {
    traverserReturns(true);

    assertThat(useCase(false).check(TUPLE, null)).isTrue();

    verifyNoInteractions(tupleCache, cacheKeyStrategy, snapshotClock);
  }
}
