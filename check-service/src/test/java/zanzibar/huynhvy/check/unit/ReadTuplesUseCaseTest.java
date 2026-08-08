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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
import zanzibar.huynhvy.check.domain.ReadTuplesResult;
import zanzibar.huynhvy.check.domain.ReadTuplesUseCase;
import zanzibar.huynhvy.check.repository.RelationTupleEntity;
import zanzibar.huynhvy.check.repository.TupleReadRepository;
import zanzibar.huynhvy.shared.domain.RelationTuple;

class ReadTuplesUseCaseTest {

  private final TupleReadRepository repository = mock(TupleReadRepository.class);
  private final ReadTuplesUseCase useCase = new ReadTuplesUseCase(repository);

  @BeforeEach
  void setUp() {
    when(repository.findByFilter(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
  }

  @Test
  void namespace_is_required() {
    assertThatThrownBy(() -> useCase.read("  ", null, null, null, 0, ""))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(repository);
  }

  @Test
  void blank_filters_become_null_wildcards() {
    useCase.read("doc", "", "   ", null, 0, "");

    verify(repository).findByFilter(eq("doc"), isNull(), isNull(), isNull(), isNull(), any());
  }

  @Test
  void present_filters_are_passed_through() {
    useCase.read("doc", "report.pdf", "viewer", "user:bob", 10, "");

    verify(repository)
        .findByFilter(eq("doc"), eq("report.pdf"), eq("viewer"), eq("user:bob"), isNull(), any());
  }

  @Test
  void page_size_is_clamped_and_fetches_one_extra() {
    ArgumentCaptor<Limit> limit = ArgumentCaptor.forClass(Limit.class);

    useCase.read("doc", null, null, null, 0, ""); // <= 0 -> default 100
    useCase.read("doc", null, null, null, 5000, ""); // > max -> 1000
    useCase.read("doc", null, null, null, 50, ""); // in range -> 50

    verify(repository, times(3)).findByFilter(any(), any(), any(), any(), any(), limit.capture());
    // one extra row is fetched to detect a further page
    assertThat(limit.getAllValues()).extracting(Limit::max).containsExactly(101, 1001, 51);
  }

  @Test
  void a_page_token_is_decoded_to_the_after_id() {
    String token =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("42".getBytes(StandardCharsets.UTF_8));

    useCase.read("doc", null, null, null, 10, token);

    verify(repository).findByFilter(eq("doc"), isNull(), isNull(), isNull(), eq(42L), any());
  }

  @Test
  void an_invalid_page_token_is_rejected() {
    assertThatThrownBy(() -> useCase.read("doc", null, null, null, 10, "!!!not-base64!!!"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_full_page_returns_a_next_token_and_trims_the_extra_row() {
    RelationTupleEntity r1 = entity(1L, "doc", "a", "viewer", "user:bob");
    RelationTupleEntity r2 = entity(2L, "doc", "b", "viewer", "user:bob");
    RelationTupleEntity r3 = entity(3L, "doc", "c", "viewer", "user:bob"); // the extra row
    when(repository.findByFilter(any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(r1, r2, r3)); // page size 2 + 1 extra

    ReadTuplesResult result = useCase.read("doc", null, null, null, 2, "");

    assertThat(result.tuples()).hasSize(2);
    assertThat(result.nextPageToken())
        .isEqualTo(
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("2".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void a_partial_page_has_no_next_token() {
    RelationTupleEntity only = entity(1L, "doc", "a", "viewer", "user:bob");
    when(repository.findByFilter(any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(only));

    ReadTuplesResult result = useCase.read("doc", null, null, null, 2, "");

    assertThat(result.tuples())
        .containsExactly(new RelationTuple("doc", "a", "viewer", "user:bob"));
    assertThat(result.nextPageToken()).isEmpty();
  }

  private static RelationTupleEntity entity(
      long id, String namespace, String objectId, String relation, String subjectId) {
    RelationTupleEntity entity = mock(RelationTupleEntity.class);
    when(entity.getId()).thenReturn(id);
    when(entity.getNamespace()).thenReturn(namespace);
    when(entity.getObjectId()).thenReturn(objectId);
    when(entity.getRelation()).thenReturn(relation);
    when(entity.getSubjectId()).thenReturn(subjectId);
    return entity;
  }
}
