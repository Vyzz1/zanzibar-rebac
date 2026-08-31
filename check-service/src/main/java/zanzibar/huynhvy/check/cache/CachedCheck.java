package zanzibar.huynhvy.check.cache;

import java.util.Map;

/**
 * A cached Check result plus what it depended on: the snapshot it was evaluated at (DB clock, epoch
 * nanos) and the config version of every namespace consulted while evaluating it.
 *
 * <p>The snapshot lets a Zookie-scoped Check decide whether the entry is fresh enough to serve.
 *
 * <p>{@code configVersions} exists because a namespace config change rewrites what a relation means
 * without touching a single tuple, so nothing evicts the entry — the cache key names the question,
 * not the rules used to answer it. A check on {@code doc} routinely consults other namespaces too
 * (a {@code group:eng#member} userset, a {@code folder:root} parent), and which ones is decided by
 * tuple data mid-traversal, so it cannot be known before evaluating. Recording them lets the reader
 * verify afterwards that every rule it relied on still holds.
 *
 * <p>Empty for entries written before this was recorded: unverifiable, so treated as unusable
 * rather than assumed current.
 */
public record CachedCheck(
    boolean allowed, long snapshotNanos, Map<String, Integer> configVersions) {}
