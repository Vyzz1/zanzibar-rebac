package zanzibar.huynhvy.check.exception;

import zanzibar.huynhvy.shared.exception.ZanzibarException;

/**
 * Thrown when {@code GraphTraverser} re-enters a relation node already on the current evaluation
 * path — an infinite recursion the traversal refuses to follow. namespace-manager rejects
 * config-time cycles, but data-driven cycles (e.g. folder A's parent is B whose parent is A) can
 * still arise at runtime.
 */
public class CyclicRelationException extends ZanzibarException {

  public CyclicRelationException(String node) {
    super("Cyclic relation detected at " + node);
  }
}
