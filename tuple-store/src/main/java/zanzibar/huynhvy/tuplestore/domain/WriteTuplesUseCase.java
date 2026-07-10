package zanzibar.huynhvy.tuplestore.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.tuplestore.outbox.OutboxEvent;
import zanzibar.huynhvy.tuplestore.outbox.OutboxRepository;
import zanzibar.huynhvy.tuplestore.repository.TupleWriteRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class WriteTuplesUseCase {

  private static final String EVENT_TYPE = "TUPLE_CREATED";

  private final TupleWriteRepository tupleWriteRepository;
  private final OutboxRepository outboxRepository;
  private final ZookieMinter zookieMinter;
  private final ObjectMapper objectMapper;

  /**
   * Writes each tuple and, in the same transaction, enqueues an outbox event so the change is never
   * lost even if the service crashes before publishing.
   */
  @Transactional
  public Zookie execute(List<RelationTuple> tuples) {
    if (tuples == null || tuples.isEmpty()) {
      throw new IllegalArgumentException("Tuples must not be empty");
    }
    tuples.forEach(this::validateTuple);

    OffsetDateTime commitTimestamp = null;
    for (RelationTuple tuple : tuples) {
      commitTimestamp = tupleWriteRepository.save(tuple);
      outboxRepository.save(OutboxEvent.create(aggregateId(tuple), EVENT_TYPE, toJson(tuple)));
      log.info("Wrote tuple {} committed at {}", tuple, commitTimestamp);
    }

    return zookieMinter.mint(commitTimestamp);
  }

  private void validateTuple(RelationTuple tuple) {
    if (isBlank(tuple.namespace())
        || isBlank(tuple.objectId())
        || isBlank(tuple.relation())
        || isBlank(tuple.subjectId())) {
      throw new IllegalArgumentException("Tuple fields must not be empty: " + tuple);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String aggregateId(RelationTuple tuple) {
    return tuple.namespace()
        + ":"
        + tuple.objectId()
        + "#"
        + tuple.relation()
        + "@"
        + tuple.subjectId();
  }

  private String toJson(RelationTuple tuple) {
    try {
      return objectMapper.writeValueAsString(tuple);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize tuple to JSON: " + tuple, e);
    }
  }
}
