package zanzibar.huynhvy.tuplestore.repository;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.springframework.stereotype.Repository;
import zanzibar.huynhvy.shared.domain.RelationTuple;

/**
 * JOOQ-based writer. JPA is deliberately avoided here so the insert can use {@code RETURNING
 * commit_timestamp}, which is the source of truth for the Zookie.
 */
@Repository
@RequiredArgsConstructor
public class TupleWriteRepositoryImpl implements TupleWriteRepository {

  private final DSLContext dsl;

  @Override
  public OffsetDateTime save(RelationTuple tuple) {
    return dsl.insertInto(table("relation_tuples"))
        .set(field("namespace", String.class), tuple.namespace())
        .set(field("object_id", String.class), tuple.objectId())
        .set(field("relation", String.class), tuple.relation())
        .set(field("subject_id", String.class), tuple.subjectId())
        .returning(field("commit_timestamp"))
        .fetchOne()
        .get("commit_timestamp", OffsetDateTime.class);
  }

  @Override
  public int delete(RelationTuple tuple) {
    return dsl.deleteFrom(table("relation_tuples"))
        .where(field("namespace", String.class).eq(tuple.namespace()))
        .and(field("object_id", String.class).eq(tuple.objectId()))
        .and(field("relation", String.class).eq(tuple.relation()))
        .and(field("subject_id", String.class).eq(tuple.subjectId()))
        .execute();
  }

  @Override
  public OffsetDateTime currentTimestamp() {
    Field<OffsetDateTime> now = field("clock_timestamp()", OffsetDateTime.class);
    return dsl.select(now).fetchOne(now);
  }
}
