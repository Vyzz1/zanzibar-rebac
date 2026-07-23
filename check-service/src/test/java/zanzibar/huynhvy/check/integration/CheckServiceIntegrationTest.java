package zanzibar.huynhvy.check.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import zanzibar.huynhvy.check.domain.CheckUseCase;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;

/**
 * End-to-end check against a real Postgres. check-service's Flyway is off (tuple-store owns the
 * table), so we disable Hibernate validation and provision {@code relation_tuples} here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=none")
class CheckServiceIntegrationTest extends BaseIntegrationTest {

  private static final RelationTuple BOB_VIEWER =
      new RelationTuple("doc", "report.pdf", "viewer", "user:bob");

  @Autowired private CheckUseCase checkUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void provisionSchema() {
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
  }

  @Test
  void allows_when_a_matching_tuple_exists() {
    seed(BOB_VIEWER);

    assertThat(checkUseCase.check(BOB_VIEWER, null)).isTrue();
  }

  @Test
  void denies_when_no_matching_tuple_exists() {
    seed(BOB_VIEWER);

    assertThat(
            checkUseCase.check(new RelationTuple("doc", "report.pdf", "editor", "user:bob"), null))
        .isFalse();
  }

  @Test
  void rejects_a_tampered_zookie_before_looking_up() {
    assertThatThrownBy(() -> checkUseCase.check(BOB_VIEWER, "not-a-valid-zookie"))
        .isInstanceOf(IllegalArgumentException.class);
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
