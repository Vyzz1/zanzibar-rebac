-- infra/init-db.sql
--
-- Runs ONCE, the first time the PostgreSQL container initializes an empty data
-- directory. The official `postgres` image executes every *.sql placed in
-- /docker-entrypoint-initdb.d/ against POSTGRES_DB (here: `zanzibar`).
--
-- The services share one database but are isolated by a schema + a
-- least-privilege login role each. This enforces the ownership boundaries from
-- AGENTS.md at the database level, not merely by convention:
--
--   tuple-store       -> owns schema `tuplestore` (relation_tuples, outbox_events)
--   namespace-manager -> owns schema `namespace`  (namespace_configs)
--   check-service     -> READ-ONLY on `tuplestore` (can never write)
--   watch-service     -> no database access at all
--
-- Tables are created by each service's Flyway migrations, NOT here. This file
-- only provisions roles, schemas, and grants.

-- ─── tuple-store: the write side ─────────────────────────────────────────────
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'tuplestore_user') THEN
    CREATE ROLE tuplestore_user LOGIN PASSWORD 'tuplestore_pass';
  END IF;
END $$;

CREATE SCHEMA IF NOT EXISTS tuplestore AUTHORIZATION tuplestore_user;
ALTER ROLE tuplestore_user SET search_path TO tuplestore;

-- ─── namespace-manager: the config side ──────────────────────────────────────
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'namespace_user') THEN
    CREATE ROLE namespace_user LOGIN PASSWORD 'namespace_pass';
  END IF;
END $$;

CREATE SCHEMA IF NOT EXISTS namespace AUTHORIZATION namespace_user;
ALTER ROLE namespace_user SET search_path TO namespace;

-- ─── check-service: read-only consumer of tuple-store's data ─────────────────
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'checkservice_user') THEN
    CREATE ROLE checkservice_user LOGIN PASSWORD 'checkservice_pass';
  END IF;
END $$;

ALTER ROLE checkservice_user SET search_path TO tuplestore;

-- It may read the tuplestore schema, but has no INSERT/UPDATE/DELETE.
GRANT USAGE ON SCHEMA tuplestore TO checkservice_user;
GRANT SELECT ON ALL TABLES IN SCHEMA tuplestore TO checkservice_user;

-- Tables tuple-store creates later (via Flyway) are automatically readable by
-- check-service — and stay off-limits for writes.
ALTER DEFAULT PRIVILEGES FOR ROLE tuplestore_user IN SCHEMA tuplestore
  GRANT SELECT ON TABLES TO checkservice_user;
