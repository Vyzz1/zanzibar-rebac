package zanzibar.huynhvy.check.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import zanzibar.huynhvy.check.domain.GraphTraverser;
import zanzibar.huynhvy.check.domain.NamespaceConfigProvider;
import zanzibar.huynhvy.shared.domain.RelationTuple;
import zanzibar.huynhvy.shared.domain.Zookie;
import zanzibar.huynhvy.shared.security.ZookieValidator;

@ExtendWith(MockitoExtension.class)
class CheckUseCaseTest {

  private static final RelationTuple TUPLE =
      new RelationTuple("doc", "report.pdf", "viewer", "user:bob");

  @Mock private GraphTraverser graphTraverser;
  @Mock private NamespaceConfigProvider namespaceConfigProvider;
  @Mock private ZookieValidator zookieValidator;
  @InjectMocks private CheckUseCase checkUseCase;

  @Test
  void allows_when_the_traverser_grants_the_relation() {
    when(evaluate()).thenReturn(true);

    assertThat(checkUseCase.check(TUPLE, null)).isTrue();
  }

  @Test
  void denies_when_the_traverser_refuses() {
    when(evaluate()).thenReturn(false);

    assertThat(checkUseCase.check(TUPLE, null)).isFalse();
  }

  @Test
  void skips_validation_when_zookie_is_blank() {
    when(evaluate()).thenReturn(true);

    checkUseCase.check(TUPLE, "");

    verifyNoInteractions(zookieValidator);
  }

  @Test
  void validates_a_present_zookie_before_evaluating() {
    when(zookieValidator.validate(new Zookie("zk"))).thenReturn(true);
    when(evaluate()).thenReturn(true);

    assertThat(checkUseCase.check(TUPLE, "zk")).isTrue();
    verify(zookieValidator).validate(new Zookie("zk"));
  }

  @Test
  void rejects_an_invalid_zookie_without_evaluating() {
    when(zookieValidator.validate(new Zookie("bad"))).thenReturn(false);

    assertThatThrownBy(() -> checkUseCase.check(TUPLE, "bad"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(graphTraverser, never()).evaluate(any(), any(), any(), any(), any());
  }

  private boolean evaluate() {
    return graphTraverser.evaluate(
        eq("doc"), eq("report.pdf"), eq("viewer"), eq("user:bob"), any());
  }
}
