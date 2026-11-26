# ADR 0001 — Keep PostgreSQL on port 5432

- Date: 2026-11-24
- Status: accepted

## Context

Kilasy's Compose file publishes PostgreSQL on host port 5432 and the application connects to `jdbc:postgresql://localhost:5432/kilasy`. On my development machine a host PostgreSQL service already listens on 5432, so the Kilasy container could not start.

## Decision

Keep the repository's original port 5432 in `compose.yaml` and `application.properties`. Resolve the conflict on the workstation instead: stop the host service (`sudo systemctl stop postgresql`) before `docker compose up -d`.

## Alternative considered

Publish the container on 5433 and change the JDBC URL. This was implemented and worked, and it lets both databases run at the same time. I reverted it because it changes shared project configuration to fix a problem specific to one machine: every other student, the teacher's instructions and the README assume 5432.

## Consequences

- The repository stays identical to the reference configuration; no project file depends on my machine.
- The host PostgreSQL must be stopped before working on Kilasy. Its data is kept. The service is still enabled, so it restarts after a reboot and must be stopped again.
- If the conflict becomes frequent, a non-committed override (for example a local `compose.override.yaml` plus `SPRING_DATASOURCE_URL`) would be preferable to changing the shared defaults.
