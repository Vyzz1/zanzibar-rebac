package zanzibar.huynhvy.check.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.grpc.Status;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import zanzibar.huynhvy.check.client.NamespaceConfigClient;
import zanzibar.huynhvy.check.domain.ExpandUseCase;
import zanzibar.huynhvy.check.domain.NamespaceConfigView;
import zanzibar.huynhvy.shared.domain.ExpandTree;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;

/**
 * Exercises Expand against a real Postgres. Flyway is off (tuple-store owns the table), so we
 * provision {@code relation_tuples} here. The gRPC config client is mocked; an unstubbed namespace
 * resolves to {@code NOT_FOUND} → empty config → direct-lookup (a {@code This} leaf).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = {"spring.jpa.hibernate.ddl-auto=none", "namespace.config.cache-ttl-seconds=0"})
class ExpandIntegrationTest extends BaseIntegrationTest {

  @Autowired private ExpandUseCase expandUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private NamespaceConfigClient namespaceConfigClient;

  @BeforeEach
  void setUp() {
    doThrow(Status.NOT_FOUND.asRuntimeException()).when(namespaceConfigClient).fetch(anyString());

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
  void a_config_less_relation_expands_to_a_leaf_of_its_subjects() {
    seed(new RelationTuple("doc", "report.pdf", "viewer", "user:bob"));
    seed(new RelationTuple("doc", "report.pdf", "viewer", "group:eng#member"));

    ExpandTree tree = expandUseCase.expand("doc", "report.pdf", "viewer");

    assertThat(tree).isInstanceOf(ExpandTree.Leaf.class);
    assertThat(((ExpandTree.Leaf) tree).subjects())
        .containsExactlyInAnyOrder("user:bob", "group:eng#member");
  }

  @Test
  void a_union_config_expands_each_branch_as_a_leaf() {
    doReturn(
            new NamespaceConfigView(
                1,
                Map.of(
                    "viewer",
                    new UsersetRewrite.Union(
                        List.of(
                            new UsersetRewrite.This(),
                            new UsersetRewrite.ComputedUserset("editor"))))))
        .when(namespaceConfigClient)
        .fetch("doc-union");
    seed(new RelationTuple("doc-union", "report.pdf", "viewer", "user:bob"));
    seed(new RelationTuple("doc-union", "report.pdf", "editor", "user:alice"));

    ExpandTree tree = expandUseCase.expand("doc-union", "report.pdf", "viewer");

    assertThat(tree)
        .isEqualTo(
            new ExpandTree.Union(
                List.of(
                    new ExpandTree.Leaf(List.of("user:bob")),
                    new ExpandTree.Leaf(List.of("user:alice")))));
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
