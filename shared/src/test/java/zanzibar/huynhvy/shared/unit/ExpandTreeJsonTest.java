package zanzibar.huynhvy.shared.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.shared.domain.ExpandTree;
import zanzibar.huynhvy.shared.domain.ExpandTree.Exclusion;
import zanzibar.huynhvy.shared.domain.ExpandTree.Intersection;
import zanzibar.huynhvy.shared.domain.ExpandTree.Leaf;
import zanzibar.huynhvy.shared.domain.ExpandTree.Union;

class ExpandTreeJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void leaf_serializes_as_a_tagged_wrapper_object() throws Exception {
    Leaf leaf = new Leaf(List.of("user:bob", "group:eng#member"));
    assertThat(mapper.writeValueAsString(leaf))
        .isEqualTo("{\"leaf\":{\"subjects\":[\"user:bob\",\"group:eng#member\"]}}");
    assertThat(roundTrip(leaf)).isEqualTo(leaf);
  }

  @Test
  void a_nested_union_round_trips_with_its_children() throws Exception {
    ExpandTree tree =
        new Union(List.of(new Leaf(List.of("user:bob")), new Leaf(List.of("user:alice"))));

    assertThat(roundTrip(tree)).isEqualTo(tree);
  }

  @Test
  void intersection_and_exclusion_round_trip() throws Exception {
    ExpandTree intersection =
        new Intersection(List.of(new Leaf(List.of("user:bob")), new Leaf(List.of("user:alice"))));
    ExpandTree exclusion =
        new Exclusion(new Leaf(List.of("user:bob")), new Leaf(List.of("user:banned")));

    assertThat(roundTrip(intersection)).isEqualTo(intersection);
    assertThat(roundTrip(exclusion)).isEqualTo(exclusion);
  }

  private ExpandTree roundTrip(ExpandTree value) throws Exception {
    return mapper.readValue(mapper.writeValueAsString(value), ExpandTree.class);
  }
}
