package zanzibar.huynhvy.check.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.check.cache.TupleCache;
import zanzibar.huynhvy.check.domain.NamespaceConfigProvider;
import zanzibar.huynhvy.shared.domain.NamespaceChange;

/**
 * Applies a namespace config change immediately instead of waiting out a TTL.
 *
 * <p>Two caches hold work derived from the old rules and both must go: the in-process config cache,
 * which would otherwise keep evaluating against them, and the Redis results computed from them.
 * Evicting only the config cache would leave the answers; evicting only the answers would let them
 * be recomputed from the same stale rules.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NamespaceChangeCacheEvicter {

  private final ObjectMapper objectMapper;
  private final NamespaceConfigProvider configProvider;
  private final TupleCache tupleCache;

  @RabbitListener(queues = "#{namespaceChangeQueue.name}")
  public void onNamespaceChange(String message) {
    NamespaceChange change;
    try {
      change = objectMapper.readValue(message, NamespaceChange.class);
    } catch (JsonProcessingException e) {
      log.warn("Discarding unparseable namespace-change message: {}", message, e);
      return;
    }

    configProvider.evict(change.namespace());
    long evicted = tupleCache.evictNamespace(change.namespace());
    log.info(
        "Namespace '{}' moved to config version {}; dropped the cached config and {} cached"
            + " check(s)",
        change.namespace(),
        change.version(),
        evicted);
  }
}
