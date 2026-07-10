-- V1: initial schema for namespace-manager (Flyway default schema: namespace).

CREATE TABLE namespace_configs (
    id         BIGSERIAL   PRIMARY KEY,
    namespace  TEXT        NOT NULL,
    version    INTEGER     NOT NULL,
    config     JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_namespace_version UNIQUE (namespace, version)
);

CREATE INDEX idx_namespace_configs_namespace
    ON namespace_configs (namespace);
