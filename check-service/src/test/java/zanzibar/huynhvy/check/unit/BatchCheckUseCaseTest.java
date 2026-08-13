package zanzibar.huynhvy.check.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.check.domain.BatchCheckUseCase;
import zanzibar.huynhvy.check.domain.BatchCheckUseCase.BatchItem;
import zanzibar.huynhvy.check.domain.CheckUseCase;
import zanzibar.huynhvy.shared.domain.RelationTuple;

class BatchCheckUseCaseTest {

  private final CheckUseCase checkUseCase = mock(CheckUseCase.class);
  private final BatchCheckUseCase useCase = new BatchCheckUseCase(checkUseCase);

  @Test
  void returns_a_result_per_item_in_request_order() {
    RelationTuple a = new RelationTuple("doc", "a", "viewer", "user:bob");
    RelationTuple b = new RelationTuple("doc", "b", "viewer", "user:bob");
    RelationTuple c = new RelationTuple("doc", "c", "viewer", "user:bob");
    when(checkUseCase.check(a, null)).thenReturn(true);
    when(checkUseCase.check(b, null)).thenReturn(false);
    when(checkUseCase.check(c, null)).thenReturn(true);

    List<Boolean> results = useCase.checkAll(List.of(item(a), item(b), item(c)));

    assertThat(results).containsExactly(true, false, true);
  }

  @Test
  void an_empty_batch_returns_no_results() {
    assertThat(useCase.checkAll(List.of())).isEmpty();
  }

  @Test
  void a_failed_check_fails_the_whole_batch() {
    RelationTuple tuple = new RelationTuple("doc", "a", "viewer", "user:bob");
    when(checkUseCase.check(tuple, "bad"))
        .thenThrow(new IllegalArgumentException("Invalid Zookie"));

    assertThatThrownBy(() -> useCase.checkAll(List.of(new BatchItem(tuple, "bad"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static BatchItem item(RelationTuple tuple) {
    return new BatchItem(tuple, null);
  }
}
