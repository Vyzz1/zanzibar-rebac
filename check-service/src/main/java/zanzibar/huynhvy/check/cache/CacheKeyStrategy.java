package zanzibar.huynhvy.check.cache;

import org.springframework.stereotype.Component;
import zanzibar.huynhvy.shared.domain.RelationTuple;

/**
 * Builds the Redis key for a Check result: {@code {namespace}:{objectId}:{relation}:{subjectId}}.
 */
@Component
public class CacheKeyStrategy {

  public String key(RelationTuple tuple) {
    return tuple.namespace()
        + ":"
        + tuple.objectId()
        + ":"
        + tuple.relation()
        + ":"
        + tuple.subjectId();
  }
}
