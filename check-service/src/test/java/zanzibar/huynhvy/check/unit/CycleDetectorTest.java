package zanzibar.huynhvy.check.unit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.check.domain.CycleDetector;
import zanzibar.huynhvy.check.exception.CyclicRelationException;

class CycleDetectorTest {

  private final CycleDetector detector = new CycleDetector();

  @Test
  void re_entering_a_node_on_the_path_is_a_cycle() {
    Set<String> path = new HashSet<>();
    detector.enter(path, "doc", "report", "viewer");

    assertThatThrownBy(() -> detector.enter(path, "doc", "report", "viewer"))
        .isInstanceOf(CyclicRelationException.class);
  }

  @Test
  void a_node_can_be_revisited_after_it_is_exited() {
    Set<String> path = new HashSet<>();
    detector.enter(path, "doc", "report", "viewer");
    detector.exit(path, "doc", "report", "viewer");

    assertThatCode(() -> detector.enter(path, "doc", "report", "viewer"))
        .doesNotThrowAnyException();
  }

  @Test
  void distinct_nodes_do_not_collide() {
    Set<String> path = new HashSet<>();

    assertThatCode(
            () -> {
              detector.enter(path, "doc", "report", "viewer");
              detector.enter(path, "doc", "report", "editor");
              detector.enter(path, "folder", "root", "viewer");
            })
        .doesNotThrowAnyException();
  }
}
