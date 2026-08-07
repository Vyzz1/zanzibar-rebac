package zanzibar.huynhvy.check.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.check.repository.TupleReadRepository;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.ComputedUserset;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Exclusion;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Intersection;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.This;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.TupleToUserset;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Union;

/**
 * Evaluates "does {@code subject} have {@code relation} on {@code object}?" by recursively
 * expanding the relation's {@link UsersetRewrite}, per Zanzibar. A relation with no configured
 * rewrite defaults to {@link This} (direct tuple lookup), so a config-less namespace still answers
 * direct checks.
 *
 * <p>Configs are resolved per namespace (a {@link TupleToUserset} may cross to another object
 * type), and {@link CycleDetector} bounds the recursion. The traverser is stateless; each call
 * carries its own path set.
 */
@Component
@RequiredArgsConstructor
public class GraphTraverser {

  private static final This DIRECT = new This();

  private final TupleReadRepository tuples;
  private final CycleDetector cycleDetector;

  /**
   * @param configs resolves a namespace to its config (relation-to-rewrite map); called lazily as
   *     traversal crosses object types
   */
  public boolean evaluate(
      String namespace,
      String objectId,
      String relation,
      String subjectId,
      Function<String, NamespaceConfigView> configs) {
    return evaluateRelation(namespace, objectId, relation, subjectId, configs, new HashSet<>());
  }

  private boolean evaluateRelation(
      String namespace,
      String objectId,
      String relation,
      String subjectId,
      Function<String, NamespaceConfigView> configs,
      Set<String> path) {
    cycleDetector.enter(path, namespace, objectId, relation);
    try {
      UsersetRewrite rewrite = configs.apply(namespace).relations().getOrDefault(relation, DIRECT);
      return evaluateRewrite(rewrite, namespace, objectId, relation, subjectId, configs, path);
    } finally {
      cycleDetector.exit(path, namespace, objectId, relation);
    }
  }

  private boolean evaluateRewrite(
      UsersetRewrite rewrite,
      String namespace,
      String objectId,
      String relation,
      String subjectId,
      Function<String, NamespaceConfigView> configs,
      Set<String> path) {
    return switch (rewrite) {
      case This ignored ->
          tuples.existsByNamespaceAndObjectIdAndRelationAndSubjectId(
              namespace, objectId, relation, subjectId);
      case ComputedUserset cu ->
          evaluateRelation(namespace, objectId, cu.relation(), subjectId, configs, path);
      case TupleToUserset ttu ->
          evaluateTupleToUserset(ttu, namespace, objectId, subjectId, configs, path);
      case Union u ->
          u.children().stream()
              .anyMatch(
                  child ->
                      evaluateRewrite(
                          child, namespace, objectId, relation, subjectId, configs, path));
      case Intersection i ->
          i.children().stream()
              .allMatch(
                  child ->
                      evaluateRewrite(
                          child, namespace, objectId, relation, subjectId, configs, path));
      case Exclusion e ->
          evaluateRewrite(e.base(), namespace, objectId, relation, subjectId, configs, path)
              && !evaluateRewrite(
                  e.subtract(), namespace, objectId, relation, subjectId, configs, path);
    };
  }

  private boolean evaluateTupleToUserset(
      TupleToUserset ttu,
      String namespace,
      String objectId,
      String subjectId,
      Function<String, NamespaceConfigView> configs,
      Set<String> path) {
    List<String> targets =
        tuples.findSubjectIdsByNamespaceAndObjectIdAndRelation(namespace, objectId, ttu.tupleset());
    for (String target : targets) {
      int sep = target.indexOf(':');
      if (sep <= 0) {
        continue; // not an "objectType:objectId" reference; can't follow it
      }
      String targetNamespace = target.substring(0, sep);
      String targetObjectId = target.substring(sep + 1);
      if (evaluateRelation(
          targetNamespace, targetObjectId, ttu.computedUserset(), subjectId, configs, path)) {
        return true;
      }
    }
    return false;
  }
}
