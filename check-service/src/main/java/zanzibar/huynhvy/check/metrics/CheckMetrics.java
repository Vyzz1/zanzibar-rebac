package zanzibar.huynhvy.check.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Meters for the check hot path, exported to Prometheus.
 *
 * <ul>
 *   <li>{@code zanzibar.check.cache{result=hit|stale|config_changed|miss}} — how often a cached
 *       result is served, rejected because a Zookie demanded something fresher, rejected because a
 *       namespace config it relied on changed, or absent. A rising {@code stale} share means
 *       callers are frequently reading right after their own writes; {@code config_changed} spikes
 *       after a namespace config is published and should settle back to zero.
 *   <li>{@code zanzibar.check.duration{result=allowed|denied}} — end-to-end check latency.
 * </ul>
 */
@Component
public class CheckMetrics {

  private static final String CACHE_METRIC = "zanzibar.check.cache";
  private static final String DURATION_METRIC = "zanzibar.check.duration";

  private final MeterRegistry registry;
  private final Counter cacheHits;
  private final Counter cacheStale;
  private final Counter cacheConfigChanged;
  private final Counter cacheMisses;
  private final Timer allowedDuration;
  private final Timer deniedDuration;

  public CheckMetrics(MeterRegistry registry) {
    this.registry = registry;
    this.cacheHits = cacheCounter(registry, "hit");
    this.cacheStale = cacheCounter(registry, "stale");
    this.cacheConfigChanged = cacheCounter(registry, "config_changed");
    this.cacheMisses = cacheCounter(registry, "miss");
    this.allowedDuration = durationTimer(registry, "allowed");
    this.deniedDuration = durationTimer(registry, "denied");
  }

  /** A cached result was fresh enough and served without evaluating. */
  public void cacheHit() {
    cacheHits.increment();
  }

  /** A cached result existed but was older than the caller's Zookie, so it was re-evaluated. */
  public void cacheStale() {
    cacheStale.increment();
  }

  /**
   * A cached result was discarded because a namespace config it relied on has since changed.
   * Counted apart from {@code hit} on purpose: it is the one outcome that would otherwise be
   * recorded as a success while a rule change went unapplied.
   */
  public void cacheConfigChanged() {
    cacheConfigChanged.increment();
  }

  /** No cached result for the key. */
  public void cacheMiss() {
    cacheMisses.increment();
  }

  public Timer.Sample start() {
    return Timer.start(registry);
  }

  public void record(Timer.Sample sample, boolean allowed) {
    sample.stop(allowed ? allowedDuration : deniedDuration);
  }

  private static Counter cacheCounter(MeterRegistry registry, String result) {
    return Counter.builder(CACHE_METRIC)
        .description("Check cache lookups by outcome")
        .tag("result", result)
        .register(registry);
  }

  private static Timer durationTimer(MeterRegistry registry, String result) {
    return Timer.builder(DURATION_METRIC)
        .description("Check latency by outcome")
        .tag("result", result)
        .register(registry);
  }
}
