package zanzibar.huynhvy.check.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.check.cache.TupleCache;
import zanzibar.huynhvy.check.config.RabbitConfig;
import zanzibar.huynhvy.shared.domain.RelationTuple;

/**
 * Evicts cached Check results when a tuple changes. Consumes tuple-store's tuple-change stream (the
 * JSON of a {@link RelationTuple}) and clears the cache for the whole object, so a re-check
 * reflects the write immediately instead of waiting for the TTL or a Zookie.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TupleChangeCacheEvicter {

  private final ObjectMapper objectMapper;
  private final TupleCache tupleCache;

  @RabbitListener(queues = RabbitConfig.CHECK_QUEUE)
  public void onTupleChange(String message) {
    RelationTuple tuple;
    try {
      tuple = objectMapper.readValue(message, RelationTuple.class);
    } catch (JsonProcessingException e) {
      log.warn("Discarding unparseable tuple-change message: {}", message, e);
      return;
    }

    long evicted = tupleCache.evictObject(tuple.namespace(), tuple.objectId());
    log.debug(
        "Evicted {} cached check(s) for {}:{} after a tuple change",
        evicted,
        tuple.namespace(),
        tuple.objectId());
  }
}
