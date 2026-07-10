-- V1: initial write-side schema for tuple-store (Flyway default schema: tuplestore).

CREATE TABLE relation_tuples (
    id               BIGSERIAL   PRIMARY KEY,
    namespace        TEXT        NOT NULL,
    object_id        TEXT        NOT NULL,
    relation         TEXT        NOT NULL,
    subject_id       TEXT        NOT NULL,
    commit_timestamp TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_relation_tuple UNIQUE (namespace, object_id, relation, subject_id)
);

-- Hot-path lookup: "who has <relation> on <namespace>:<object_id>?"
CREATE INDEX idx_relation_tuples_object
    ON relation_tuples (namespace, object_id, relation);

-- Reverse lookup by subject (expand / list-objects style queries).
CREATE INDEX idx_relation_tuples_subject
    ON relation_tuples (namespace, relation, subject_id);

CREATE TABLE outbox_events (
    id           BIGSERIAL   PRIMARY KEY,
    aggregate_id TEXT        NOT NULL,
    event_type   TEXT        NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published    BOOLEAN     NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ
);

-- OutboxPoller drains unpublished rows with FOR UPDATE SKIP LOCKED; a partial
-- index keeps that scan cheap as published rows accumulate.
CREATE INDEX idx_outbox_unpublished
    ON outbox_events (id)
    WHERE published = FALSE;
