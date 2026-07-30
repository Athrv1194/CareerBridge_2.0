-- Creates the seven CareerBridge databases. Mounted at /docker-entrypoint-initdb.d/init.sql, so the
-- postgres image runs it once, on first boot only (empty data directory). Re-running it by hand
-- against a live server is safe -- see the guard below.
--
-- PostgreSQL has no CREATE DATABASE IF NOT EXISTS. That syntax exists in MySQL, which this project
-- migrated away from, and a plain CREATE DATABASE against an existing name is a hard error that
-- ON_ERROR_STOP=1 (what the postgres entrypoint uses) turns into a failed container start.
-- The portable idiom is to SELECT the statement text conditionally and let psql's \gexec execute
-- whatever the query returned -- zero rows when the database already exists, so nothing runs.
--
-- \gexec is a psql meta-command, not SQL. It works here because the entrypoint feeds this file to
-- psql; it will not work through a JDBC connection or a GUI client's query tab.
--
-- CREATE DATABASE cannot run inside a transaction block. The entrypoint does not pass
-- --single-transaction, so this is fine as written -- do not wrap it in BEGIN/COMMIT.

SELECT 'CREATE DATABASE careerbridge_auth'
 WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'careerbridge_auth')\gexec

SELECT 'CREATE DATABASE careerbridge_student'
 WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'careerbridge_student')\gexec

SELECT 'CREATE DATABASE careerbridge_assessment'
 WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'careerbridge_assessment')\gexec

SELECT 'CREATE DATABASE careerbridge_recommendation'
 WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'careerbridge_recommendation')\gexec

-- Singular, matching the four above. notification-service's MongoDB database is
-- careerbridge_notifications (plural) and lives in Atlas, not here -- the one-character difference
-- is deliberate and already committed.
SELECT 'CREATE DATABASE careerbridge_notification'
 WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'careerbridge_notification')\gexec

-- organization-service (P1). Multi-tenancy: organizations and their departments.
SELECT 'CREATE DATABASE careerbridge_organization'
 WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'careerbridge_organization')\gexec

-- roadmap-service (P1). Career roadmap templates and per-student milestone progress.
SELECT 'CREATE DATABASE careerbridge_roadmap'
 WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'careerbridge_roadmap')\gexec
