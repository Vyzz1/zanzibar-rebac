package zanzibar.huynhvy.check.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import zanzibar.huynhvy.check.domain.ReadTuplesUseCase;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;

/**
 * Exercises the Read filter query against a real Postgres. check-service's Flyway is off
 * (tuple-store owns the table), so we disable Hibernate validation and provision {@code
 * relation_tuples} here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
class ReadTuplesIntegrationTest extends BaseIntegrationTest {

  @Autowired private ReadTuplesUseCase readTuplesUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void seedFixture() {
    jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS tuplestore");
    jdbcTemplate.execute(
        """
        CREATE TABLE IF NOT EXISTS tuplestore.relation_tuples (
          id               BIGSERIAL   PRIMARY KEY,
          namespace        TEXT        NOT NULL,
          object_id        TEXT        NOT NULL,
          relation         TEXT        NOT NULL,
          subject_id       TEXT        NOT NULL,
          commit_timestamp TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
        )
        """);
    jdbcTemplate.execute("TRUNCATE tuplestore.relation_tuples");

    seed(new RelationTuple("doc", "report.pdf", "viewer", "user:bob"));
    seed(new RelationTuple("doc", "report.pdf", "editor", "user:alice"));
    seed(new RelationTuple("doc", "other.pdf", "viewer", "user:bob"));
    seed(new RelationTuple("folder", "root", "viewer", "user:carol")); // different namespace
  }

  @Test
  void reads_every_tuple_in_a_namespace_and_isolates_others() {
    List<RelationTuple> result = readTuplesUseCase.read("doc", null, null, null, 0);

    assertThat(result)
        .containsExactlyInAnyOrder(
            new RelationTuple("doc", "report.pdf", "viewer", "user:bob"),
            new RelationTuple("doc", "report.pdf", "editor", "user:alice"),
            new RelationTuple("doc", "other.pdf", "viewer", "user:bob"));
    // the folder-namespace tuple is not included
  }

  @Test
  void filters_by_object_id() {
    List<RelationTuple> result = readTuplesUseCase.read("doc", "report.pdf", null, null, 0);

    assertThat(result)
        .extracting(RelationTuple::relation)
        .containsExactlyInAnyOrder("viewer", "editor");
  }

  @Test
  void filters_by_relation() {
    List<RelationTuple> result = readTuplesUseCase.read("doc", null, "viewer", null, 0);

    assertThat(result)
        .extracting(RelationTuple::objectId)
        .containsExactlyInAnyOrder("report.pdf", "other.pdf");
  }

  @Test
  void filters_by_subject_id() {
    List<RelationTuple> result = readTuplesUseCase.read("doc", null, null, "user:alice", 0);

    assertThat(result)
        .containsExactly(new RelationTuple("doc", "report.pdf", "editor", "user:alice"));
  }

  @Test
  void caps_the_result_to_the_page_size() {
    List<RelationTuple> result = readTuplesUseCase.read("doc", null, null, null, 2);

    assertThat(result).hasSize(2);
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
}
