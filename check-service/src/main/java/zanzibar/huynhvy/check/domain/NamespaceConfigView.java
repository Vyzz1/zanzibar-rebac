package zanzibar.huynhvy.check.domain;

import java.util.Map;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;

/**
 * A namespace's userset rewrite config as seen by the check path: the relation-to-rewrite map plus
 * the version it came from. A relation absent from {@code relations} defaults to direct lookup
 * ({@code This}), so {@link #EMPTY} means "every relation is direct-only".
 */
public record NamespaceConfigView(int version, Map<String, UsersetRewrite> relations) {

  public static final NamespaceConfigView EMPTY = new NamespaceConfigView(0, Map.of());
}
