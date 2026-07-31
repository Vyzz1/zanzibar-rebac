package zanzibar.huynhvy.check.domain;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.check.client.NamespaceConfigClient;

/**
 * Supplies namespace config to the check path, cached in memory for a short TTL — configs change
 * far less often than checks are served, so the hot path shouldn't hit namespace-manager per call.
 * A namespace with no config degrades to {@link NamespaceConfigView#EMPTY} (direct lookup only).
 */
@Slf4j
@Component
public class NamespaceConfigProvider {

  private final NamespaceConfigClient client;
  private final long ttlNanos;
  private final ConcurrentHashMap<String, CachedEntry> cache = new ConcurrentHashMap<>();

  public NamespaceConfigProvider(
      NamespaceConfigClient client,
      @Value("${namespace.config.cache-ttl-seconds:30}") long ttlSeconds) {
    this.client = client;
    this.ttlNanos = ttlSeconds * 1_000_000_000L;
  }

  public NamespaceConfigView get(String namespace) {
    long now = System.nanoTime();
    CachedEntry cached = cache.get(namespace);
    if (cached != null && cached.expiresAtNanos - now > 0) {
      return cached.view;
    }
    NamespaceConfigView view = load(namespace);
    cache.put(namespace, new CachedEntry(view, now + ttlNanos));
    return view;
  }

  private NamespaceConfigView load(String namespace) {
    try {
      return client.fetch(namespace);
    } catch (StatusRuntimeException e) {
      if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
        log.debug(
            "No config for namespace '{}'; treating relations as direct-lookup only", namespace);
        return NamespaceConfigView.EMPTY;
      }
      throw e;
    }
  }

  private record CachedEntry(NamespaceConfigView view, long expiresAtNanos) {}
}
