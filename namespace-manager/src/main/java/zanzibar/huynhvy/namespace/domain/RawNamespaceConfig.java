package zanzibar.huynhvy.namespace.domain;

/**
 * A stored config as-is: the raw {@code configJson} straight from the row, without parsing into the
 * {@code UsersetRewrite} model. Used by the gRPC path, which ships the JSON to check-service to
 * parse — keeping a single source of truth for the model.
 */
public record RawNamespaceConfig(String namespace, int version, String configJson) {}
