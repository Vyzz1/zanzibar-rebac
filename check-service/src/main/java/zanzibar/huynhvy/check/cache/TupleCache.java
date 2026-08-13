package zanzibar.huynhvy.check.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Stores Check results in Redis as {@code "<0|1>|<snapshotNanos>"} with a TTL. Reads never throw on
 * a corrupt or missing value — they return empty so the caller falls back to a fresh evaluation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TupleCache {

  private final StringRedisTemplate redis;

  public Optional<CachedCheck> get(String key) {
    String value = redis.opsForValue().get(key);
    if (value == null) {
      return Optional.empty();
    }
    int sep = value.indexOf('|');
    if (sep != 1) {
      return Optional.empty();
    }
    try {
      boolean allowed = value.charAt(0) == '1';
      long snapshotNanos = Long.parseLong(value.substring(sep + 1));
      return Optional.of(new CachedCheck(allowed, snapshotNanos));
    } catch (NumberFormatException e) {
      log.warn("Discarding corrupt cache value for key '{}'", key);
      return Optional.empty();
    }
  }

  public void put(String key, boolean allowed, long snapshotNanos, Duration ttl) {
    redis.opsForValue().set(key, (allowed ? "1" : "0") + "|" + snapshotNanos, ttl);
  }

  /**
   * Evicts every cached Check result for an object — all relations and subjects under {@code
   * {namespace}:{objectId}:*}. A tuple change invalidates the whole object so same-object derived
   * relations (e.g. viewer computed from editor) don't serve stale results. Uses SCAN, not KEYS, to
   * avoid blocking Redis.
   *
   * @return the number of keys removed
   */
  public long evictObject(String namespace, String objectId) {
    String pattern = namespace + ":" + objectId + ":*";
    List<String> keys = new ArrayList<>();
    try (Cursor<String> cursor =
        redis.scan(ScanOptions.scanOptions().match(pattern).count(256).build())) {
      cursor.forEachRemaining(keys::add);
    }
    if (keys.isEmpty()) {
      return 0;
    }
    Long removed = redis.delete(keys);
    return removed == null ? 0 : removed;
  }
}
