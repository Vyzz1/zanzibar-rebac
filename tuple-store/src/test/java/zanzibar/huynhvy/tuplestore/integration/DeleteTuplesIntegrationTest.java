package zanzibar.huynhvy.tuplestore.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;
import zanzibar.huynhvy.tuplestore.domain.DeleteTuplesUseCase;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DeleteTuplesIntegrationTest extends BaseIntegrationTest {

  private static final RelationTuple BOB_VIEWER =
      new RelationTuple("doc", "report.pdf", "viewer", "user:bob");

  @Autowired private DeleteTuplesUseCase deleteTuplesUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clean() {
    jdbcTemplate.execute("TRUNCATE tuplestore.relation_tuples, tuplestore.outbox_events");
  }

  @Test
  void delete_removes_the_row_and_enqueues_a_delete_event() {
    seed(BOB_VIEWER);

    Zookie zookie = deleteTuplesUseCase.execute(List.of(BOB_VIEWER));

    assertThat(zookie.token()).isNotBlank();
    assertThat(countTuples()).isZero();
    assertThat(countOutboxOfType("TUPLE_DELETED")).isEqualTo(1);
  }

  @Test
  void deleting_a_missing_tuple_is_a_no_op_but_still_returns_a_zookie() {
    Zookie zookie =
        deleteTuplesUseCase.execute(
            List.of(new RelationTuple("doc", "ghost.pdf", "viewer", "user:nobody")));

    assertThat(zookie.token()).isNotBlank();
    assertThat(countAllOutbox()).isZero();
  }

  private void seed(RelationTuple tuple) {
    jdbcTemplate.update(
        "INSERT INTO tuplestore.relation_tuples(namespace, object_id, relation, subject_id)"
            + " VALUES (?, ?, ?, ?)",
        tuple.namespace(),
        tuple.objectId(),
        tuple.relation(),
        tuple.subjectId());
  }

  private Integer countTuples() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM tuplestore.relation_tuples", Integer.class);
  }

  private Integer countOutboxOfType(String eventType) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM tuplestore.outbox_events WHERE event_type = ?",
        Integer.class,
        eventType);
  }

  private Integer countAllOutbox() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM tuplestore.outbox_events", Integer.class);
  }
}
