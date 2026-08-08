package zanzibar.huynhvy.check.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.check.domain.CycleDetector;
import zanzibar.huynhvy.check.domain.ExpandUseCase;
import zanzibar.huynhvy.check.domain.NamespaceConfigProvider;
import zanzibar.huynhvy.check.domain.NamespaceConfigView;
import zanzibar.huynhvy.check.exception.CyclicRelationException;
import zanzibar.huynhvy.check.repository.TupleReadRepository;
import zanzibar.huynhvy.shared.domain.ExpandTree;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;

class ExpandUseCaseTest {

  private final TupleReadRepository tuples = mock(TupleReadRepository.class);
  private final NamespaceConfigProvider configProvider = mock(NamespaceConfigProvider.class);
  private final ExpandUseCase useCase =
      new ExpandUseCase(tuples, new CycleDetector(), configProvider);

  @BeforeEach
  void setUp() {
    when(configProvider.get(any())).thenReturn(NamespaceConfigView.EMPTY);
  }

  @Test
  void this_expands_to_a_leaf_of_the_stored_subjects() {
    when(tuples.findSubjectIdsByNamespaceAndObjectIdAndRelation("doc", "report", "viewer"))
        .thenReturn(List.of("user:bob", "group:eng#member"));

    assertThat(useCase.expand("doc", "report", "viewer"))
        .isEqualTo(new ExpandTree.Leaf(List.of("user:bob", "group:eng#member")));
  }

  @Test
  void union_of_this_and_computed_userset_expands_both_branches() {
    when(configProvider.get("doc"))
        .thenReturn(
            new NamespaceConfigView(
                1,
                Map.of(
                    "viewer",
                    new UsersetRewrite.Union(
                        List.of(
                            new UsersetRewrite.This(),
                            new UsersetRewrite.ComputedUserset("editor"))))));
    when(tuples.findSubjectIdsByNamespaceAndObjectIdAndRelation("doc", "report", "viewer"))
        .thenReturn(List.of("user:bob"));
    when(tuples.findSubjectIdsByNamespaceAndObjectIdAndRelation("doc", "report", "editor"))
        .thenReturn(List.of("user:alice"));

    assertThat(useCase.expand("doc", "report", "viewer"))
        .isEqualTo(
            new ExpandTree.Union(
                List.of(
                    new ExpandTree.Leaf(List.of("user:bob")),
                    new ExpandTree.Leaf(List.of("user:alice")))));
  }

  @Test
  void tuple_to_userset_expands_to_a_union_over_the_targets() {
    when(configProvider.get("doc"))
        .thenReturn(
            new NamespaceConfigView(
                1, Map.of("viewer", new UsersetRewrite.TupleToUserset("parent", "viewer"))));
    when(tuples.findSubjectIdsByNamespaceAndObjectIdAndRelation("doc", "report", "parent"))
        .thenReturn(List.of("folder:root"));
    when(tuples.findSubjectIdsByNamespaceAndObjectIdAndRelation("folder", "root", "viewer"))
        .thenReturn(List.of("user:carol"));

    assertThat(useCase.expand("doc", "report", "viewer"))
        .isEqualTo(new ExpandTree.Union(List.of(new ExpandTree.Leaf(List.of("user:carol")))));
  }

  @Test
  void a_relation_cycle_is_rejected() {
    when(configProvider.get("doc"))
        .thenReturn(
            new NamespaceConfigView(
                1,
                Map.of(
                    "viewer", new UsersetRewrite.ComputedUserset("editor"),
                    "editor", new UsersetRewrite.ComputedUserset("viewer"))));

    assertThatThrownBy(() -> useCase.expand("doc", "report", "viewer"))
        .isInstanceOf(CyclicRelationException.class);
  }

  @Test
  void blank_arguments_are_rejected() {
    assertThatThrownBy(() -> useCase.expand("doc", "", "viewer"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
