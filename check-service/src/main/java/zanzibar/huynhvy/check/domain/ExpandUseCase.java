package zanzibar.huynhvy.check.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import zanzibar.huynhvy.check.repository.TupleReadRepository;
import zanzibar.huynhvy.shared.domain.ExpandTree;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;

/**
 * Expands the effective userset tree for an {@code object#relation} (admin/debug — never called
 * from Check). Walks the relation's {@link UsersetRewrite} like {@code GraphTraverser}, but builds
 * a tree instead of a boolean and takes no subject: {@code This} becomes a {@link ExpandTree.Leaf}
 * of the stored subjects, {@code ComputedUserset} inlines its subtree, and {@code TupleToUserset}
 * becomes a union over its targets' subtrees. {@link CycleDetector} bounds the recursion.
 */
@Service
@RequiredArgsConstructor
public class ExpandUseCase {

  private static final UsersetRewrite DIRECT = new UsersetRewrite.This();

  private final TupleReadRepository tuples;
  private final CycleDetector cycleDetector;
  private final NamespaceConfigProvider namespaceConfigProvider;

  public ExpandTree expand(String namespace, String objectId, String relation) {
    if (isBlank(namespace) || isBlank(objectId) || isBlank(relation)) {
      throw new IllegalArgumentException("namespace, object_id and relation are required");
    }
    return expandRelation(namespace, objectId, relation, new HashSet<>());
  }

  private ExpandTree expandRelation(
      String namespace, String objectId, String relation, Set<String> path) {
    cycleDetector.enter(path, namespace, objectId, relation);
    try {
      UsersetRewrite rewrite =
          namespaceConfigProvider.get(namespace).relations().getOrDefault(relation, DIRECT);
      return expandRewrite(rewrite, namespace, objectId, relation, path);
    } finally {
      cycleDetector.exit(path, namespace, objectId, relation);
    }
  }

  private ExpandTree expandRewrite(
      UsersetRewrite rewrite,
      String namespace,
      String objectId,
      String relation,
      Set<String> path) {
    return switch (rewrite) {
      case UsersetRewrite.This ignored ->
          new ExpandTree.Leaf(
              tuples.findSubjectIdsByNamespaceAndObjectIdAndRelation(
                  namespace, objectId, relation));
      case UsersetRewrite.ComputedUserset cu ->
          expandRelation(namespace, objectId, cu.relation(), path);
      case UsersetRewrite.TupleToUserset ttu ->
          expandTupleToUserset(ttu, namespace, objectId, path);
      case UsersetRewrite.Union u ->
          new ExpandTree.Union(
              u.children().stream()
                  .map(child -> expandRewrite(child, namespace, objectId, relation, path))
                  .toList());
      case UsersetRewrite.Intersection i ->
          new ExpandTree.Intersection(
              i.children().stream()
                  .map(child -> expandRewrite(child, namespace, objectId, relation, path))
                  .toList());
      case UsersetRewrite.Exclusion e ->
          new ExpandTree.Exclusion(
              expandRewrite(e.base(), namespace, objectId, relation, path),
              expandRewrite(e.subtract(), namespace, objectId, relation, path));
    };
  }

  private ExpandTree expandTupleToUserset(
      UsersetRewrite.TupleToUserset ttu, String namespace, String objectId, Set<String> path) {
    List<ExpandTree> children = new ArrayList<>();
    for (String target :
        tuples.findSubjectIdsByNamespaceAndObjectIdAndRelation(
            namespace, objectId, ttu.tupleset())) {
      int sep = target.indexOf(':');
      if (sep <= 0) {
        continue; // not an "objectType:objectId" reference; can't follow it
      }
      children.add(
          expandRelation(
              target.substring(0, sep), target.substring(sep + 1), ttu.computedUserset(), path));
    }
    return new ExpandTree.Union(children);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
