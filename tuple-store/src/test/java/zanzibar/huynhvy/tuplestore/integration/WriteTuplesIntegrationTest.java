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
import zanzibar.huynhvy.tuplestore.domain.WriteTuplesUseCase;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WriteTuplesIntegrationTest extends BaseIntegrationTest {

  @Autowired private WriteTuplesUseCase writeTuplesUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clean() {
    jdbcTemplate.execute("TRUNCATE tuplestore.relation_tuples, tuplestore.outbox_events");
  }

  @Test
  void write_persists_the_tuple_and_an_outbox_event_atomically() {
    RelationTuple tuple = new RelationTuple("doc", "report.pdf", "viewer", "user:bob");

    Zookie zookie = writeTuplesUseCase.execute(List.of(tuple));

    assertThat(zookie.token()).isNotBlank();
    assertThat(countTuples()).isEqualTo(1);
    assertThat(countUnpublishedEvents()).isEqualTo(1);
  }

  @Test
  void the_jooq_insert_populates_the_db_generated_commit_timestamp() {
    writeTuplesUseCase.execute(
        List.of(new RelationTuple("doc", "report.pdf", "viewer", "user:bob")));

    Integer withTimestamp =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM tuplestore.relation_tuples WHERE commit_timestamp IS NOT NULL",
            Integer.class);
    assertThat(withTimestamp).isEqualTo(1);
  }

  private Integer countTuples() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM tuplestore.relation_tuples", Integer.class);
  }

  private Integer countUnpublishedEvents() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM tuplestore.outbox_events WHERE published = false", Integer.class);
  }
}
