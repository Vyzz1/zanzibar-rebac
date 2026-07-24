package zanzibar.huynhvy.shared.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.ComputedUserset;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Exclusion;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Intersection;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.This;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.TupleToUserset;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Union;

class UsersetRewriteJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void this_serializes_as_a_tagged_wrapper_object() throws Exception {
    assertThat(mapper.writeValueAsString(new This())).isEqualTo("{\"this\":{}}");
    assertThat(roundTrip(new This())).isInstanceOf(This.class);
  }

  @Test
  void computed_userset_carries_its_relation() throws Exception {
    String json = mapper.writeValueAsString(new ComputedUserset("editor"));
    assertThat(json).isEqualTo("{\"computedUserset\":{\"relation\":\"editor\"}}");
    assertThat(roundTrip(new ComputedUserset("editor"))).isEqualTo(new ComputedUserset("editor"));
  }

  @Test
  void tuple_to_userset_round_trips() throws Exception {
    TupleToUserset value = new TupleToUserset("parent", "viewer");
    assertThat(roundTrip(value)).isEqualTo(value);
  }

  @Test
  void a_nested_union_round_trips_with_its_children() throws Exception {
    UsersetRewrite viewer =
        new Union(
            List.of(
                new This(), new ComputedUserset("editor"), new TupleToUserset("parent", "viewer")));

    assertThat(roundTrip(viewer)).isEqualTo(viewer);
  }

  @Test
  void intersection_and_exclusion_round_trip() throws Exception {
    UsersetRewrite intersection =
        new Intersection(List.of(new ComputedUserset("member"), new ComputedUserset("active")));
    UsersetRewrite exclusion =
        new Exclusion(new ComputedUserset("member"), new ComputedUserset("banned"));

    assertThat(roundTrip(intersection)).isEqualTo(intersection);
    assertThat(roundTrip(exclusion)).isEqualTo(exclusion);
  }

  private UsersetRewrite roundTrip(UsersetRewrite value) throws Exception {
    return mapper.readValue(mapper.writeValueAsString(value), UsersetRewrite.class);
  }
}
