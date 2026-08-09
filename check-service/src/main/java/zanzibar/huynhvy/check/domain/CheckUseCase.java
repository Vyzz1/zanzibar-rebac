package zanzibar.huynhvy.check.domain;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zanzibar.huynhvy.check.cache.CacheKeyStrategy;
import zanzibar.huynhvy.check.cache.CachedCheck;
import zanzibar.huynhvy.check.cache.TupleCache;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.shared.security.ZookieValidator;

/**
 * Answers "does {@code subject} have {@code relation} on {@code object}?" — served from a Redis
 * cache when a fresh-enough entry exists, otherwise evaluated via {@link GraphTraverser} and
 * cached.
 *
 * <p>A Zookie (if present) is a freshness floor: a cached result is only served when its snapshot
 * is at least as recent as the Zookie's commit timestamp, so a caller never sees a result older
 * than a write it has already observed. Without a Zookie any cached result is served (best-effort).
 */
@Slf4j
@Service
public class CheckUseCase {

  private final GraphTraverser graphTraverser;
  private final NamespaceConfigProvider namespaceConfigProvider;
  private final ZookieValidator zookieValidator;
  private final TupleCache tupleCache;
  private final CacheKeyStrategy cacheKeyStrategy;
  private final SnapshotClock snapshotClock;
  private final boolean cacheEnabled;
  private final Duration cacheTtl;

  public CheckUseCase(
      GraphTraverser graphTraverser,
      NamespaceConfigProvider namespaceConfigProvider,
      ZookieValidator zookieValidator,
      TupleCache tupleCache,
      CacheKeyStrategy cacheKeyStrategy,
      SnapshotClock snapshotClock,
      @Value("${check.cache.enabled:true}") boolean cacheEnabled,
      @Value("${check.cache.ttl-seconds:60}") long cacheTtlSeconds) {
    this.graphTraverser = graphTraverser;
    this.namespaceConfigProvider = namespaceConfigProvider;
    this.zookieValidator = zookieValidator;
    this.tupleCache = tupleCache;
    this.cacheKeyStrategy = cacheKeyStrategy;
    this.snapshotClock = snapshotClock;
    this.cacheEnabled = cacheEnabled;
    this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
  }

  /**
   * @param zookie optional consistency token; if present it must carry a valid HMAC and acts as a
   *     freshness floor
   */
  public boolean check(RelationTuple tuple, String zookie) {
    OptionalLong freshnessFloor = OptionalLong.empty();
    if (zookie != null && !zookie.isBlank()) {
      freshnessFloor = zookieValidator.readTimestampNanos(new Zookie(zookie));
      if (freshnessFloor.isEmpty()) {
        throw new IllegalArgumentException("Invalid Zookie");
      }
    }

    if (!cacheEnabled) {
      return evaluate(tuple);
    }

    String key = cacheKeyStrategy.key(tuple);
    Optional<CachedCheck> cached = tupleCache.get(key);
    if (cached.isPresent() && isFreshEnough(cached.get(), freshnessFloor)) {
      log.debug("Check {} -> {} (cache hit)", tuple, cached.get().allowed());
      return cached.get().allowed();
    }

    // Stamp the snapshot BEFORE evaluating so it never claims to be fresher than the data read.
    long snapshotNanos = snapshotClock.nowNanos();
    boolean allowed = evaluate(tuple);
    tupleCache.put(key, allowed, snapshotNanos, cacheTtl);
    return allowed;
  }

  private boolean evaluate(RelationTuple tuple) {
    boolean allowed =
        graphTraverser.evaluate(
            tuple.namespace(),
            tuple.objectId(),
            tuple.relation(),
            tuple.subjectId(),
            namespaceConfigProvider::get);
    log.debug("Check {} -> {}", tuple, allowed);
    return allowed;
  }

  private static boolean isFreshEnough(CachedCheck cached, OptionalLong freshnessFloor) {
    return freshnessFloor.isEmpty() || cached.snapshotNanos() >= freshnessFloor.getAsLong();
  }
}
