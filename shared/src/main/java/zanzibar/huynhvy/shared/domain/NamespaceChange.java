package zanzibar.huynhvy.shared.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The payload published when a namespace's config is replaced, mirroring how {@link RelationTuple}
 * is the payload of a tuple change. Shared because it is a wire contract between namespace-manager
 * and its consumers, not a model owned by either.
 *
 * <p>Carries no rewrite rules: a consumer is told only that the rules for {@code namespace} moved
 * to {@code version}, and re-reads them if it needs them. That keeps the event small and stops it
 * from becoming a second, staler copy of the config.
 */
// Tolerant of unknown fields on the type itself, not on whichever mapper happens to deserialize
// it: a producer that starts sending a new field must not break consumers deployed before it.
@JsonIgnoreProperties(ignoreUnknown = true)
public record NamespaceChange(String namespace, int version) {}
