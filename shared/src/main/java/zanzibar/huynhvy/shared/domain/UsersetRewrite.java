package zanzibar.huynhvy.shared.domain;

// Intended permits: Union, Intersection, Exclusion, This, ComputedUserset.
public sealed interface UsersetRewrite permits UsersetRewrite.Placeholder {
  final class Placeholder implements UsersetRewrite {
    private Placeholder() {}
  }
}
