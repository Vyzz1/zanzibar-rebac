package zanzibar.huynhvy.check.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
import zanzibar.huynhvy.check.domain.ReadTuplesUseCase;
import zanzibar.huynhvy.check.repository.RelationTupleEntity;
import zanzibar.huynhvy.check.repository.TupleReadRepository;
import zanzibar.huynhvy.shared.domain.RelationTuple;

class ReadTuplesUseCaseTest {

  private final TupleReadRepository repository = mock(TupleReadRepository.class);
  private final ReadTuplesUseCase useCase = new ReadTuplesUseCase(repository);

  @BeforeEach
  void setUp() {
    when(repository.findByFilter(any(), any(), any(), any(), any())).thenReturn(List.of());
  }

  @Test
  void namespace_is_required() {
    assertThatThrownBy(() -> useCase.read("  ", null, null, null, 0))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void blank_filters_become_null_wildcards() {
    useCase.read("doc", "", "   ", null, 0);

    verify(repository).findByFilter(eq("doc"), isNull(), isNull(), isNull(), any());
  }

  @Test
  void present_filters_are_passed_through() {
    useCase.read("doc", "report.pdf", "viewer", "user:bob", 10);

    verify(repository)
        .findByFilter(eq("doc"), eq("report.pdf"), eq("viewer"), eq("user:bob"), any());
  }

  @Test
  void page_size_is_clamped() {
    ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);

    useCase.read("doc", null, null, null, 0); // <= 0 -> default
    useCase.read("doc", null, null, null, 5000); // > max -> max
    useCase.read("doc", null, null, null, 50); // in range -> itself

    verify(repository, times(3)).findByFilter(any(), any(), any(), any(), limit.capture());
    assertThat(limit.getAllValues())
        .extracting(Limit::max)
        .containsExactly(100, 1000, 50); // default, max-cap, in-range
  }

  @Test
  void maps_entities_to_domain_tuples() {
    RelationTupleEntity stored = entity("doc", "report.pdf", "viewer", "user:bob");
    when(repository.findByFilter(any(), any(), any(), any(), any())).thenReturn(List.of(stored));

    List<RelationTuple> result = useCase.read("doc", null, null, null, 0);

    assertThat(result)
        .containsExactly(new RelationTuple("doc", "report.pdf", "viewer", "user:bob"));
  }

  private static RelationTupleEntity entity(
      String namespace, String objectId, String relation, String subjectId) {
    RelationTupleEntity entity = mock(RelationTupleEntity.class);
    when(entity.getNamespace()).thenReturn(namespace);
    when(entity.getObjectId()).thenReturn(objectId);
    when(entity.getRelation()).thenReturn(relation);
    when(entity.getSubjectId()).thenReturn(subjectId);
    return entity;
  }
}
