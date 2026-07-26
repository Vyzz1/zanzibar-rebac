package zanzibar.huynhvy.namespace.domain;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import zanzibar.huynhvy.namespace.exception.InvalidNamespaceConfigException;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.ComputedUserset;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Exclusion;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Intersection;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.This;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.TupleToUserset;
import zanzibar.huynhvy.shared.domain.UsersetRewrite.Union;

/**
 * Validates a namespace config <em>at write time</em> so the check path never has to deal with a
 * broken config. Rejects:
 *
 * <ul>
 *   <li>a rewrite referencing a relation not defined in the namespace;
 *   <li>a cycle in the {@link ComputedUserset} graph (a relation that, following only
 *       ComputedUserset edges, can reach itself). {@link TupleToUserset} is data-dependent and
 *       crosses objects, so it does not form a config-time cycle.
 * </ul>
 */
@Service
public class ValidateNamespaceUseCase {

  public void validate(Map<String, UsersetRewrite> relations) {
    if (relations == null || relations.isEmpty()) {
      throw new InvalidNamespaceConfigException("A namespace must define at least one relation");
    }
    checkReferences(relations);
    checkForCycles(relations);
  }

  private void checkReferences(Map<String, UsersetRewrite> relations) {
    for (var entry : relations.entrySet()) {
      Set<String> referenced = new HashSet<>();
      collectReferencedRelations(entry.getValue(), referenced);
      for (String ref : referenced) {
        if (!relations.containsKey(ref)) {
          throw new InvalidNamespaceConfigException(
              "Relation '%s' references undefined relation '%s'".formatted(entry.getKey(), ref));
        }
      }
    }
  }

  /** All relations a rewrite depends on: ComputedUserset targets and TupleToUserset tuplesets. */
  private void collectReferencedRelations(UsersetRewrite node, Set<String> out) {
    switch (node) {
      case This ignored -> {
        // direct lookup references no relation
      }
      case ComputedUserset c -> out.add(c.relation());
      case TupleToUserset t -> out.add(t.tupleset()); // tupleset is a relation on this object
      case Union u -> u.children().forEach(child -> collectReferencedRelations(child, out));
      case Intersection i -> i.children().forEach(child -> collectReferencedRelations(child, out));
      case Exclusion e -> {
        collectReferencedRelations(e.base(), out);
        collectReferencedRelations(e.subtract(), out);
      }
    }
  }

  private void checkForCycles(Map<String, UsersetRewrite> relations) {
    Set<String> done = new HashSet<>();
    Set<String> onPath = new HashSet<>();
    for (String relation : relations.keySet()) {
      if (reachesItself(relation, relations, done, onPath)) {
        throw new InvalidNamespaceConfigException(
            "Cycle detected in relation rewrites involving '%s'".formatted(relation));
      }
    }
  }

  private boolean reachesItself(
      String relation,
      Map<String, UsersetRewrite> relations,
      Set<String> done,
      Set<String> onPath) {
    if (onPath.contains(relation)) {
      return true;
    }
    if (done.contains(relation)) {
      return false;
    }
    onPath.add(relation);

    Set<String> edges = new HashSet<>();
    collectComputedEdges(relations.get(relation), edges);
    for (String next : edges) {
      if (relations.containsKey(next) && reachesItself(next, relations, done, onPath)) {
        return true;
      }
    }

    onPath.remove(relation);
    done.add(relation);
    return false;
  }

  /** Only ComputedUserset forms a config-time edge; This and TupleToUserset do not. */
  private void collectComputedEdges(UsersetRewrite node, Set<String> out) {
    switch (node) {
      case ComputedUserset c -> out.add(c.relation());
      case Union u -> u.children().forEach(child -> collectComputedEdges(child, out));
      case Intersection i -> i.children().forEach(child -> collectComputedEdges(child, out));
      case Exclusion e -> {
        collectComputedEdges(e.base(), out);
        collectComputedEdges(e.subtract(), out);
      }
      case This ignored -> {
        // no edge
      }
      case TupleToUserset ignored -> {
        // data-dependent, crosses objects — not a config-time edge
      }
    }
  }
}
