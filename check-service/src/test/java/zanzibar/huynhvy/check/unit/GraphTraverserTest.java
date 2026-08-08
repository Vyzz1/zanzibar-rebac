package zanzibar.huynhvy.check.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.check.domain.CycleDetector;
import zanzibar.huynhvy.check.domain.GraphTraverser;
import zanzibar.huynhvy.check.domain.NamespaceConfigView;
import zanzibar.huynhvy.check.exception.CyclicRelationException;
import zanzibar.huynhvy.check.repository.TupleReadRepository;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.ComputedUserset;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Exclusion;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Intersection;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.This;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.TupleToUserset;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Union;

class GraphTraverserTest {

  private static final String BOB = "user:bob";

  private final TupleReadRepository tuples = mock(TupleReadRepository.class);
  private final GraphTraverser traverser = new GraphTraverser(tuples, new CycleDetector());

  @Test
  void direct_lookup_when_relation_has_no_configured_rewrite() {
    directTuple("doc", "report", "viewer", BOB);

    assertThat(check("doc", "report", "viewer", BOB, noConfig())).isTrue();
    assertThat(check("doc", "report", "editor", BOB, noConfig())).isFalse();
  }

  @Test
  void computed_userset_grants_via_another_relation_on_the_same_object() {
    // viewer = union(this, computedUserset(editor)); bob is only an editor.
    directTuple("doc", "report", "editor", BOB);
    Function<String, NamespaceConfigView> configs =
        config(
            "doc", Map.of("viewer", new Union(List.of(new This(), new ComputedUserset("editor")))));

    assertThat(check("doc", "report", "viewer", BOB, configs)).isTrue();
  }

  @Test
  void tuple_to_userset_follows_a_relation_to_another_object() {
    // doc viewer = viewers of the parent folder; folder viewer is direct.
    when(tuples.findSubjectIdsByNamespaceAndObjectIdAndRelation("doc", "report", "parent"))
        .thenReturn(List.of("folder:root"));
    directTuple("folder", "root", "viewer", BOB);
    Function<String, NamespaceConfigView> configs =
        config(
            "doc", Map.of("viewer", new TupleToUserset("parent", "viewer")),
            "folder", Map.of("viewer", new This()));

    assertThat(check("doc", "report", "viewer", BOB, configs)).isTrue();
  }

  @Test
  void intersection_requires_every_child() {
    directTuple("doc", "report", "editor", BOB); // editor yes, owner no
    Function<String, NamespaceConfigView> configs =
        config(
            "doc",
            Map.of(
                "viewer",
                new Intersection(
                    List.of(new ComputedUserset("editor"), new ComputedUserset("owner")))));

    assertThat(check("doc", "report", "viewer", BOB, configs)).isFalse();

    directTuple("doc", "report", "owner", BOB); // now both hold
    assertThat(check("doc", "report", "viewer", BOB, configs)).isTrue();
  }

  @Test
  void exclusion_subtracts_the_second_child() {
    // viewer = editor AND NOT banned.
    directTuple("doc", "report", "editor", BOB);
    Function<String, NamespaceConfigView> configs =
        config(
            "doc",
            Map.of(
                "viewer",
                new Exclusion(new ComputedUserset("editor"), new ComputedUserset("banned"))));

    assertThat(check("doc", "report", "viewer", BOB, configs)).isTrue();

    directTuple("doc", "report", "banned", BOB);
    assertThat(check("doc", "report", "viewer", BOB, configs)).isFalse();
  }

  @Test
  void a_relation_cycle_is_rejected() {
    // viewer -> editor -> viewer, with no tuple to terminate the recursion.
    Function<String, NamespaceConfigView> configs =
        config(
            "doc",
            Map.of(
                "viewer", new ComputedUserset("editor"),
                "editor", new ComputedUserset("viewer")));

    assertThatThrownBy(() -> check("doc", "report", "viewer", BOB, configs))
        .isInstanceOf(CyclicRelationException.class);
  }

  @Test
  void expands_a_userset_subject_so_a_group_member_is_granted() {
    // report#viewer@group:eng#member ; group:eng#member@user:bob (bob is granted indirectly).
    when(tuples.findUsersetSubjectIds("doc", "report", "viewer"))
        .thenReturn(List.of("group:eng#member"));
    directTuple("group", "eng", "member", BOB);

    assertThat(check("doc", "report", "viewer", BOB, noConfig())).isTrue();
  }

  @Test
  void denies_when_the_subject_is_not_in_the_granted_userset() {
    // report#viewer@group:eng#member, but bob is not a member of group:eng.
    when(tuples.findUsersetSubjectIds("doc", "report", "viewer"))
        .thenReturn(List.of("group:eng#member"));

    assertThat(check("doc", "report", "viewer", BOB, noConfig())).isFalse();
  }

  @Test
  void expands_nested_group_memberships() {
    // viewer -> group:eng#member -> group:leads#member -> user:bob
    when(tuples.findUsersetSubjectIds("doc", "report", "viewer"))
        .thenReturn(List.of("group:eng#member"));
    when(tuples.findUsersetSubjectIds("group", "eng", "member"))
        .thenReturn(List.of("group:leads#member"));
    directTuple("group", "leads", "member", BOB);

    assertThat(check("doc", "report", "viewer", BOB, noConfig())).isTrue();
  }

  private boolean check(
      String namespace,
      String objectId,
      String relation,
      String subjectId,
      Function<String, NamespaceConfigView> configs) {
    return traverser.evaluate(namespace, objectId, relation, subjectId, configs);
  }

  private void directTuple(String namespace, String objectId, String relation, String subjectId) {
    when(tuples.existsByNamespaceAndObjectIdAndRelationAndSubjectId(
            namespace, objectId, relation, subjectId))
        .thenReturn(true);
  }

  private static Function<String, NamespaceConfigView> noConfig() {
    return namespace -> NamespaceConfigView.EMPTY;
  }

  private static Function<String, NamespaceConfigView> config(
      String namespace, Map<String, UsersetRewrite> relations) {
    return ns ->
        namespace.equals(ns) ? new NamespaceConfigView(1, relations) : NamespaceConfigView.EMPTY;
  }

  private static Function<String, NamespaceConfigView> config(
      String ns1,
      Map<String, UsersetRewrite> relations1,
      String ns2,
      Map<String, UsersetRewrite> relations2) {
    return ns -> {
      if (ns1.equals(ns)) {
        return new NamespaceConfigView(1, relations1);
      }
      if (ns2.equals(ns)) {
        return new NamespaceConfigView(1, relations2);
      }
      return NamespaceConfigView.EMPTY;
    };
  }
}
