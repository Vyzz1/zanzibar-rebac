package zanzibar.huynhvy.tuplestore.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import zanzibar.huynhvy.shared.domain.RelationTuple;

/** Shared helpers for the tuple write and delete paths, so they never diverge. */
final class Tuples {

  private Tuples() {}

  /** Rejects a tuple with any blank field. */
  static void requireComplete(RelationTuple tuple) {
    if (isBlank(tuple.namespace())
        || isBlank(tuple.objectId())
        || isBlank(tuple.relation())
        || isBlank(tuple.subjectId())) {
      throw new IllegalArgumentException("Tuple fields must not be empty: " + tuple);
    }
  }

  /** Stable id correlating a tuple's outbox events: {@code namespace:object#relation@subject}. */
  static String aggregateId(RelationTuple tuple) {
    return tuple.namespace()
        + ":"
        + tuple.objectId()
        + "#"
        + tuple.relation()
        + "@"
        + tuple.subjectId();
  }

  static String toJson(ObjectMapper objectMapper, RelationTuple tuple) {
    try {
      return objectMapper.writeValueAsString(tuple);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize tuple to JSON: " + tuple, e);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
