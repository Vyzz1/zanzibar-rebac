package zanzibar.huynhvy.check.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zanzibar.huynhvy.check.domain.CheckUseCase;
import zanzibar.huynhvy.check.repository.TupleReadRepository;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.shared.security.ZookieValidator;

@ExtendWith(MockitoExtension.class)
class CheckUseCaseTest {

  private static final RelationTuple TUPLE =
      new RelationTuple("doc", "report.pdf", "viewer", "user:bob");

  @Mock private TupleReadRepository tupleReadRepository;
  @Mock private ZookieValidator zookieValidator;
  @InjectMocks private CheckUseCase checkUseCase;

  @Test
  void allows_when_a_matching_tuple_exists() {
    when(existsQuery()).thenReturn(true);

    assertThat(checkUseCase.check(TUPLE, null)).isTrue();
  }

  @Test
  void denies_when_no_matching_tuple_exists() {
    when(existsQuery()).thenReturn(false);

    assertThat(checkUseCase.check(TUPLE, null)).isFalse();
  }

  @Test
  void skips_validation_when_zookie_is_blank() {
    when(existsQuery()).thenReturn(true);

    checkUseCase.check(TUPLE, "");

    verifyNoInteractions(zookieValidator);
  }

  @Test
  void validates_a_present_zookie_before_looking_up() {
    when(zookieValidator.validate(new Zookie("zk"))).thenReturn(true);
    when(existsQuery()).thenReturn(true);

    assertThat(checkUseCase.check(TUPLE, "zk")).isTrue();
    verify(zookieValidator).validate(new Zookie("zk"));
  }

  @Test
  void rejects_an_invalid_zookie_without_hitting_the_database() {
    when(zookieValidator.validate(new Zookie("bad"))).thenReturn(false);

    assertThatThrownBy(() -> checkUseCase.check(TUPLE, "bad"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(tupleReadRepository, never())
        .existsByNamespaceAndObjectIdAndRelationAndSubjectId(
            anyString(), anyString(), anyString(), anyString());
  }

  private Boolean existsQuery() {
    return tupleReadRepository.existsByNamespaceAndObjectIdAndRelationAndSubjectId(
        any(), any(), any(), any());
  }
}
