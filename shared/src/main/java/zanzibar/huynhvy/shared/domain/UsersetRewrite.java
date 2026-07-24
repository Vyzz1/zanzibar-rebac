package zanzibar.huynhvy.shared.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * How a relation is composed. Evaluated recursively by check-service's GraphTraverser; defined
 * per-namespace and stored as config by namespace-manager.
 *
 * <p>Serialized as a tagged wrapper-object so it round-trips through the namespace config JSON,
 * e.g.
 *
 * <pre>{@code
 * { "union": { "children": [
 *     { "this": {} },
 *     { "computedUserset": { "relation": "editor" } },
 *     { "tupleToUserset": { "tupleset": "parent", "computedUserset": "viewer" } }
 * ] } }
 * }</pre>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
  @JsonSubTypes.Type(value = UsersetRewrite.This.class, name = "this"),
  @JsonSubTypes.Type(value = UsersetRewrite.ComputedUserset.class, name = "computedUserset"),
  @JsonSubTypes.Type(value = UsersetRewrite.TupleToUserset.class, name = "tupleToUserset"),
  @JsonSubTypes.Type(value = UsersetRewrite.Union.class, name = "union"),
  @JsonSubTypes.Type(value = UsersetRewrite.Intersection.class, name = "intersection"),
  @JsonSubTypes.Type(value = UsersetRewrite.Exclusion.class, name = "exclusion"),
})
public sealed interface UsersetRewrite
    permits UsersetRewrite.This,
        UsersetRewrite.ComputedUserset,
        UsersetRewrite.TupleToUserset,
        UsersetRewrite.Union,
        UsersetRewrite.Intersection,
        UsersetRewrite.Exclusion {

  /** Direct tuple lookup on this object. */
  record This() implements UsersetRewrite {}

  /** Another relation on the SAME object also grants this one (e.g. editors are viewers). */
  record ComputedUserset(String relation) implements UsersetRewrite {}

  /**
   * Follow {@code tupleset} on this object to another object, then check {@code computedUserset}
   * there (e.g. viewers of the parent folder are viewers of the doc).
   */
  record TupleToUserset(String tupleset, String computedUserset) implements UsersetRewrite {}

  /** Granted if ANY child grants it. */
  record Union(List<UsersetRewrite> children) implements UsersetRewrite {}

  /** Granted only if ALL children grant it. */
  record Intersection(List<UsersetRewrite> children) implements UsersetRewrite {}

  /** Granted by {@code base} but NOT by {@code subtract}. */
  record Exclusion(UsersetRewrite base, UsersetRewrite subtract) implements UsersetRewrite {}
}
