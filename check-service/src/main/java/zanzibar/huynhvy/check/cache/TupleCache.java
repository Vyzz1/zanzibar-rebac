package zanzibar.huynhvy.check.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Stores Check results in Redis as {@code "<0|1>|<snapshotNanos>|<ns>=<version>,..."} with a TTL.
 * Reads never throw on a corrupt or missing value — they return empty so the caller falls back to a
 * fresh evaluation.
 *
 * <p>The trailing segment records the config version of every namespace the evaluation consulted;
 * see {@link CachedCheck}. Values written before it existed have two segments and are read back
 * with no versions, which the caller treats as unusable.
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
    String[] parts = value.split("\\|", 3);
    if (parts.length < 2 || parts[0].length() != 1) {
      return Optional.empty();
    }
    try {
      boolean allowed = parts[0].charAt(0) == '1';
      long snapshotNanos = Long.parseLong(parts[1]);
      Map<String, Integer> versions = parts.length == 3 ? parseVersions(parts[2]) : Map.of();
      if (versions == null) {
        log.warn("Discarding cache value with unreadable config versions for key '{}'", key);
        return Optional.empty();
      }
      return Optional.of(new CachedCheck(allowed, snapshotNanos, versions));
    } catch (NumberFormatException e) {
      log.warn("Discarding corrupt cache value for key '{}'", key);
      return Optional.empty();
    }
  }

  public void put(
      String key,
      boolean allowed,
      long snapshotNanos,
      Map<String, Integer> configVersions,
      Duration ttl) {
    String value =
        (allowed ? "1" : "0") + "|" + snapshotNanos + "|" + encodeVersions(configVersions);
    redis.opsForValue().set(key, value, ttl);
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
    return evictMatching(namespace + ":" + objectId + ":*");
  }

  /**
   * Evicts every cached Check result in a namespace. Used when its config changes: the rules behind
   * every answer in it moved at once, so there is no smaller unit to invalidate.
   *
   * <p>Answers in <em>other</em> namespaces may also have consulted these rules (a {@code doc}
   * check expanding a {@code group} userset). Those are not reachable by prefix, and are caught on
   * read instead — see {@link CachedCheck}.
   *
   * @return the number of keys removed
   */
  public long evictNamespace(String namespace) {
    return evictMatching(namespace + ":*");
  }

  private long evictMatching(String pattern) {
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

  private static String encodeVersions(Map<String, Integer> configVersions) {
    StringBuilder encoded = new StringBuilder();
    for (Map.Entry<String, Integer> entry : configVersions.entrySet()) {
      if (!encoded.isEmpty()) {
        encoded.append(',');
      }
      encoded.append(entry.getKey()).append('=').append(entry.getValue());
    }
    return encoded.toString();
  }

  /**
   * Null when the segment cannot be read, so the caller discards the entry instead of trusting it.
   */
  private static Map<String, Integer> parseVersions(String encoded) {
    if (encoded.isEmpty()) {
      return Map.of();
    }
    Map<String, Integer> versions = new HashMap<>();
    for (String pair : encoded.split(",")) {
      // A namespace never contains '=', so the last one separates it from the version.
      int separator = pair.lastIndexOf('=');
      if (separator <= 0) {
        return null;
      }
      try {
        versions.put(pair.substring(0, separator), Integer.parseInt(pair.substring(separator + 1)));
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return Map.copyOf(versions);
  }
}
