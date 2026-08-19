package zanzibar.huynhvy.tuplestore.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.tuplestore.metrics.TupleStoreMetrics;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;
import zanzibar.huynhvy.tuplestore.outbox.OutboxRepository;
import zanzibar.huynhvy.tuplestore.outbox.TupleChangeType;
import zanzibar.huynhvy.tuplestore.repository.TupleWriteRepository;

/**
 * Removes tuples (revocation) and, in the same transaction, enqueues an outbox event for each row
 * actually removed so consumers can react (Watch, cache eviction). Unlike a write, a delete is not
 * validated against the namespace config — revocation is always allowed. Deleting a tuple that does
 * not exist is a no-op but still returns a Zookie.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeleteTuplesUseCase {

  private final TupleWriteRepository tupleWriteRepository;
  private final OutboxRepository outboxRepository;
  private final ZookieMinter zookieMinter;
  private final ObjectMapper objectMapper;
  private final TupleStoreMetrics metrics;

  @Transactional
  public Zookie execute(List<RelationTuple> tuples) {
    if (tuples == null || tuples.isEmpty()) {
      throw new IllegalArgumentException("Tuples must not be empty");
    }
    tuples.forEach(Tuples::requireComplete);

    for (RelationTuple tuple : tuples) {
      int deleted = tupleWriteRepository.delete(tuple);
      if (deleted > 0) {
        outboxRepository.save(
            OutboxEvent.create(
                Tuples.aggregateId(tuple),
                TupleChangeType.DELETED.eventType(),
                Tuples.toJson(objectMapper, tuple)));
        metrics.tuplesDeleted(deleted);
        log.info("Deleted tuple {} ({} row(s))", tuple, deleted);
      }
    }

    return zookieMinter.mint(tupleWriteRepository.currentTimestamp());
  }
}
