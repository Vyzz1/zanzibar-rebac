package zanzibar.huynhvy.shared.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * The effective userset tree for an {@code object#relation}, produced by check-service's Expand and
 * serialized to JSON so any client can walk it. Mirrors the shape of {@link UsersetRewrite}: {@code
 * ComputedUserset} is inlined as its subtree and {@code TupleToUserset} as a union over its
 * targets, so the tree contains only these four node kinds.
 *
 * <p>Serialized as a tagged wrapper-object, e.g.
 *
 * <pre>{@code
 * { "union": { "children": [
 *     { "leaf": { "subjects": ["user:bob", "group:eng#member"] } },
 *     { "leaf": { "subjects": ["user:alice"] } }
 * ] } }
 * }</pre>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
  @JsonSubTypes.Type(value = ExpandTree.Leaf.class, name = "leaf"),
  @JsonSubTypes.Type(value = ExpandTree.Union.class, name = "union"),
  @JsonSubTypes.Type(value = ExpandTree.Intersection.class, name = "intersection"),
  @JsonSubTypes.Type(value = ExpandTree.Exclusion.class, name = "exclusion"),
})
public sealed interface ExpandTree
    permits ExpandTree.Leaf, ExpandTree.Union, ExpandTree.Intersection, ExpandTree.Exclusion {

  /**
   * Direct subjects granted the relation — users and usersets ({@code group:eng#member}), as-is.
   */
  record Leaf(List<String> subjects) implements ExpandTree {}

  /** Granted by ANY child. */
  record Union(List<ExpandTree> children) implements ExpandTree {}

  /** Granted only by ALL children. */
  record Intersection(List<ExpandTree> children) implements ExpandTree {}

  /** Granted by {@code base} but NOT by {@code subtract}. */
  record Exclusion(ExpandTree base, ExpandTree subtract) implements ExpandTree {}
}
