-- V2: carry the tuple's commit timestamp on its outbox event.
--
-- Watch clients need a cursor of their own: a resume token taken from the last event they actually
-- processed, not from a write they happened to make. Minting that requires the moment the change
-- committed, which until now was returned to the writer and then thrown away.
--
-- created_at is not a substitute — it is stamped by the application, so it can sit either side of
-- the real commit, and a cursor that is off by even a few milliseconds silently skips changes.
--
-- Nullable because rows written before this migration have no commit timestamp; events without one
-- are published without a resume token rather than with a wrong one.
ALTER TABLE outbox_events
    ADD COLUMN commit_timestamp TIMESTAMPTZ;
