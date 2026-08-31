-- V2: outbox for namespace config changes.
--
-- A config change rewrites what a relation means without touching a tuple, so consumers holding
-- cached answers have no way to learn of it. Publishing it makes that visible.
--
-- Same shape and reasoning as tuple-store's outbox: the event is inserted in the transaction that
-- writes the config row, so the two commit together. Publishing straight to RabbitMQ instead would
-- lose the event whenever the broker is unreachable or the process dies between commit and publish
-- — and a lost config event is exactly the stale-rules window this table exists to close.

CREATE TABLE outbox_events (
    id           BIGSERIAL   PRIMARY KEY,
    aggregate_id TEXT        NOT NULL,
    event_type   TEXT        NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published    BOOLEAN     NOT NULL DEFAULT false,
    published_at TIMESTAMPTZ
);

-- The poller only ever asks for unpublished rows, oldest first; a partial index keeps that lookup
-- off the ever-growing tail of published ones.
CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (id)
    WHERE published = false;
