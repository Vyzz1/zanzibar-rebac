package zanzibar.huynhvy.check.domain;

import java.util.Set;
import org.springframework.stereotype.Component;
import zanzibar.huynhvy.check.exception.CyclicRelationException;

/**
 * Guards a graph traversal against infinite recursion. Stateless: the caller owns a per-request
 * {@code path} set (the relation nodes currently on the DFS stack). A node is keyed by {@code
 * namespace:objectId:relation}; re-entering one already on the path is a cycle.
 */
@Component
public class CycleDetector {

  /** Marks a node as entered; throws if it is already on the current path. */
  public void enter(Set<String> path, String namespace, String objectId, String relation) {
    String node = key(namespace, objectId, relation);
    if (!path.add(node)) {
      throw new CyclicRelationException(node);
    }
  }

  /** Removes a node from the path once its subtree has been fully evaluated. */
  public void exit(Set<String> path, String namespace, String objectId, String relation) {
    path.remove(key(namespace, objectId, relation));
  }

  private static String key(String namespace, String objectId, String relation) {
    return namespace + ":" + objectId + ":" + relation;
  }
}
