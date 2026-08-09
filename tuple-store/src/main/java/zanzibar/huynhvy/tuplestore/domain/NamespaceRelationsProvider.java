package zanzibar.huynhvy.tuplestore.domain;

import io.grpc.StatusRuntimeException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.tuplestore.client.NamespaceConfigClient;

/**
 * Supplies a namespace's defined relations for write validation, cached in memory for a short TTL.
 * Best-effort: if namespace-manager is unreachable the lookup returns {@link Optional#empty()} so
 * the caller lets the write through rather than blocking it on a validation-service outage. {@code
 * Optional.empty()} therefore means both "no config" and "couldn't determine" — either way the
 * write is not rejected.
 */
@Slf4j
@Component
public class NamespaceRelationsProvider {

  private final NamespaceConfigClient client;
  private final long ttlNanos;
  private final ConcurrentHashMap<String, CachedEntry> cache = new ConcurrentHashMap<>();

  public NamespaceRelationsProvider(
      NamespaceConfigClient client,
      @Value("${tuple.validation.cache-ttl-seconds:30}") long ttlSeconds) {
    this.client = client;
    this.ttlNanos = ttlSeconds * 1_000_000_000L;
  }

  public Optional<Set<String>> definedRelations(String namespace) {
    long now = System.nanoTime();
    CachedEntry cached = cache.get(namespace);
    if (cached != null && cached.expiresAtNanos - now > 0) {
      return cached.relations;
    }
    Optional<Set<String>> relations;
    try {
      relations = client.definedRelations(namespace);
    } catch (StatusRuntimeException e) {
      log.warn(
          "Could not fetch config for namespace '{}' ({}); skipping write validation",
          namespace,
          e.getStatus().getCode());
      return Optional.empty(); // fail open; do not cache the failure
    }
    cache.put(namespace, new CachedEntry(relations, now + ttlNanos));
    return relations;
  }

  private record CachedEntry(Optional<Set<String>> relations, long expiresAtNanos) {}
}
