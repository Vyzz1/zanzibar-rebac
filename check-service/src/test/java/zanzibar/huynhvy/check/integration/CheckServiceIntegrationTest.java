package zanzibar.huynhvy.check.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import zanzibar.huynhvy.check.domain.CheckUseCase;
import zanzibar.huynhvy.check.domain.NamespaceConfigView;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.ComputedUserset;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.This;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.TupleToUserset;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Union;
import zanzibar.huynhvy.shared.testing.BaseIntegrationTest;

/**
 * End-to-end check against a real Postgres. check-service's Flyway is off (tuple-store owns the
 * table), so we disable Hibernate validation and provision {@code relation_tuples} here. The gRPC
 * config client is mocked so config is fixed per test; the cache TTL is 0 so each check re-reads
 * it. A namespace with no stubbed config resolves to {@code NOT_FOUND} → empty config → direct
 * lookup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "spring.jpa.hibernate.ddl-auto=none",
      "namespace.config.cache-ttl-seconds=0",
      "check.cache.enabled=false"
    })
class CheckServiceIntegrationTest extends BaseIntegrationTest {

  private static final RelationTuple BOB_VIEWER =
      new RelationTuple("doc", "report.pdf", "viewer", "user:bob");

  @Autowired private CheckUseCase checkUseCase;
  @Autowired private JdbcTemplate jdbcTemplate;
  @MockitoBean private NamespaceConfigClient namespaceConfigClient;

  @BeforeEach
  void setUp() {
    // Every namespace has no config unless a test says otherwise → direct lookup only.
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

  @Test
  void allows_an_editor_as_a_viewer_via_computed_userset() {
    // viewer = union(this, computedUserset(editor)); bob is only an editor.
    doReturn(
            new NamespaceConfigView(
                1, Map.of("viewer", new Union(List.of(new This(), new ComputedUserset("editor"))))))
        .when(namespaceConfigClient)
        .fetch("doc-computed");
    seed(new RelationTuple("doc-computed", "report.pdf", "editor", "user:bob"));

    assertThat(
            checkUseCase.check(
                new RelationTuple("doc-computed", "report.pdf", "viewer", "user:bob"), null))
        .isTrue();
  }

  @Test
  void allows_a_viewer_of_the_parent_folder_via_tuple_to_userset() {
    // doc viewer = viewers of the parent folder; the folder namespace has no config → direct
    // lookup.
    doReturn(new NamespaceConfigView(1, Map.of("viewer", new TupleToUserset("parent", "viewer"))))
        .when(namespaceConfigClient)
        .fetch("doc-ttu");
    seed(new RelationTuple("doc-ttu", "report.pdf", "parent", "folder:root"));
    seed(new RelationTuple("folder", "root", "viewer", "user:bob"));

    assertThat(
            checkUseCase.check(
                new RelationTuple("doc-ttu", "report.pdf", "viewer", "user:bob"), null))
        .isTrue();
  }

  @Test
  void allows_a_group_member_via_userset_subject_expansion() {
    // report#viewer is granted to the group's members; bob is a member → bob is a viewer.
    // Neither namespace is configured, so viewer/member both default to This.
    seed(new RelationTuple("doc-grp", "report.pdf", "viewer", "group:eng#member"));
    seed(new RelationTuple("group", "eng", "member", "user:bob"));

    assertThat(
            checkUseCase.check(
                new RelationTuple("doc-grp", "report.pdf", "viewer", "user:bob"), null))
        .isTrue();
  }

  @Test
  void denies_a_non_member_despite_the_group_grant() {
    seed(new RelationTuple("doc-grp", "report.pdf", "viewer", "group:eng#member"));
    seed(new RelationTuple("group", "eng", "member", "user:bob"));

    assertThat(
            checkUseCase.check(
                new RelationTuple("doc-grp", "report.pdf", "viewer", "user:carol"), null))
        .isFalse();
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
