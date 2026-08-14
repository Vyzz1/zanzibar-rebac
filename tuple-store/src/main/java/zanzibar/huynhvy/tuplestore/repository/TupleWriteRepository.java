package zanzibar.huynhvy.tuplestore.repository;

import java.time.OffsetDateTime;
import zanzibar.huynhvy.shared.domain.RelationTuple;

public interface TupleWriteRepository {

  /** Inserts the tuple and returns the DB-assigned commit timestamp. */
  OffsetDateTime save(RelationTuple relationTuple);

  /** Deletes rows matching the tuple; returns how many were removed (0 if none existed). */
  int delete(RelationTuple relationTuple);

  /** The current database clock — used to stamp the Zookie for a delete. */
  OffsetDateTime currentTimestamp();
}
