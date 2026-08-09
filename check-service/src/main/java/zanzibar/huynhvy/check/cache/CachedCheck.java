package zanzibar.huynhvy.check.cache;

/**
 * A cached Check result plus the snapshot it was evaluated at (DB clock, epoch nanos). The snapshot
 * lets a Zookie-scoped Check decide whether the entry is fresh enough to serve.
 */
public record CachedCheck(boolean allowed, long snapshotNanos) {}
