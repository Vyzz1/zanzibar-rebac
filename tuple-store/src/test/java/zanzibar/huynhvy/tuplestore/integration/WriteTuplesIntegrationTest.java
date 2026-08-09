package zanzibar.huynhvy.tuplestore.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;
import zanzibar.huynhvy.tuplestore.client.NamespaceConfigClient;
import zanzibar.huynhvy.tuplestore.domain.WriteTuplesUseCase;

/**
 * The config client is mocked (an unstubbed namespace has no config → write allowed) and the
 * validation cache TTL is 0 so each test fixes its own config with no cross-test bleed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "tuple.validation.cache-ttl-seconds=0")
class WriteTuplesIntegrationTest extends BaseIntegrationTest {

  @Autowired private WriteTuplesUseCase writeTuplesUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private NamespaceConfigClient namespaceConfigClient;

  @BeforeEach
  void clean() {
    when(namespaceConfigClient.definedRelations(any())).thenReturn(Optional.empty());
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

  @Test
  void rejects_a_relation_not_defined_in_the_config_without_writing() {
    // "doc" has a config that defines only "editor"; writing "viewer" must be rejected.
    when(namespaceConfigClient.definedRelations("doc")).thenReturn(Optional.of(Set.of("editor")));

    assertThatThrownBy(
            () ->
                writeTuplesUseCase.execute(
                    List.of(new RelationTuple("doc", "report.pdf", "viewer", "user:bob"))))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(countTuples()).isZero();
    assertThat(countUnpublishedEvents()).isZero();
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
