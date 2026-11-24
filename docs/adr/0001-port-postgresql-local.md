# ADR 0001 — Keep PostgreSQL on port 5432

- Date: 2026-11-24
- Status: accepted by explicit user instruction; host service stop pending

## Context

The host PostgreSQL service occupies port 5432 and prevents the Kilasy container from starting. An initial workaround exposed the container on 5433 and successfully started Kilasy.

## Decision

The user explicitly requested keeping port 5432 and stopping the existing host PostgreSQL instance. Restore the original Compose mapping `5432:5432` and JDBC URL `jdbc:postgresql://localhost:5432/kilasy`. Stop the host service gracefully using `sudo systemctl stop postgresql`.

## Alternative considered

Using port 5433 allowed both databases to run simultaneously. This workaround was implemented and verified, then superseded by the user's instruction.

## Consequences

The host PostgreSQL service must be stopped before the Kilasy container starts. Its data is preserved. The service remains enabled, so it may start again after a reboot. No persistent system service configuration is changed. Administrative authentication is required to stop the host service on this machine.
