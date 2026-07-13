-- Keycloak gets its own database on the shared Postgres instance (separate DB,
-- not a shared schema — Keycloak owns its own migrations). Runs only on first
-- initialisation of an empty postgres-data volume.
CREATE DATABASE keycloak;
GRANT ALL PRIVILEGES ON DATABASE keycloak TO vigilant;
