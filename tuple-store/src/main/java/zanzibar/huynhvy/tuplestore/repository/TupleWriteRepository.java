package zanzibar.huynhvy.tuplestore.repository;

import java.time.OffsetDateTime;
import zanzibar.huynhvy.shared.domain.RelationTuple;

public interface TupleWriteRepository {

  /** Inserts the tuple and returns the DB-assigned commit timestamp. */
  OffsetDateTime save(RelationTuple relationTuple);
}
