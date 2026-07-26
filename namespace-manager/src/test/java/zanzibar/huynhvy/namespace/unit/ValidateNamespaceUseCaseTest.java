package zanzibar.huynhvy.namespace.unit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.namespace.domain.ValidateNamespaceUseCase;
import zanzibar.huynhvy.namespace.exception.InvalidNamespaceConfigException;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.ComputedUserset;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.This;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.TupleToUserset;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Union;

class ValidateNamespaceUseCaseTest {

  private final ValidateNamespaceUseCase validate = new ValidateNamespaceUseCase();

  @Test
  void accepts_a_well_formed_config() {
    Map<String, UsersetRewrite> relations =
        Map.of(
            "parent", new This(),
            "owner", new This(),
            "editor", new Union(List.of(new This(), new ComputedUserset("owner"))),
            "viewer",
                new Union(
                    List.of(
                        new This(),
                        new ComputedUserset("editor"),
                        new TupleToUserset("parent", "viewer"))));

    assertThatCode(() -> validate.validate(relations)).doesNotThrowAnyException();
  }

  @Test
  void rejects_an_empty_namespace() {
    assertThatThrownBy(() -> validate.validate(Map.of()))
        .isInstanceOf(InvalidNamespaceConfigException.class);
  }

  @Test
  void rejects_a_reference_to_an_undefined_relation() {
    Map<String, UsersetRewrite> relations = Map.of("viewer", new ComputedUserset("editor"));

    assertThatThrownBy(() -> validate.validate(relations))
        .isInstanceOf(InvalidNamespaceConfigException.class)
        .hasMessageContaining("editor");
  }

  @Test
  void rejects_a_computed_userset_cycle() {
    Map<String, UsersetRewrite> relations =
        Map.of(
            "viewer", new ComputedUserset("editor"),
            "editor", new ComputedUserset("viewer"));

    assertThatThrownBy(() -> validate.validate(relations))
        .isInstanceOf(InvalidNamespaceConfigException.class)
        .hasMessageContaining("Cycle");
  }

  @Test
  void a_tuple_to_userset_self_reference_is_not_a_cycle() {
    // "viewer of the parent is a viewer" is legal — it terminates when there is no parent tuple.
    Map<String, UsersetRewrite> relations =
        Map.of(
            "parent", new This(),
            "viewer", new Union(List.of(new This(), new TupleToUserset("parent", "viewer"))));

    assertThatCode(() -> validate.validate(relations)).doesNotThrowAnyException();
  }
}
