package zanzibar.huynhvy.namespace.domain;

import java.time.OffsetDateTime;
import java.util.Map;
import zanzibar.huynhvy.shared.domain.UsersetRewrite;

/** A namespace config as returned by the API: one version, with its relations parsed. */
public record NamespaceView(
    String namespace,
    int version,
    Map<String, UsersetRewrite> relations,
    OffsetDateTime createdAt) {}
