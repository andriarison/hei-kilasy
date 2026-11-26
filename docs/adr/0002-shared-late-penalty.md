# ADR 0002 — Share the final grade calculation

- Date: 2026-11-24
- Status: implemented

## Context

C1 requires a 10% deduction from the grade for each started late day, capped at 50%. Correction, transcript and CSV previously calculated grades independently; CSV truncated while the other outputs rounded.

## Decision

Use a single `GradeCalculator` from correction, submission detail, transcript and CSV. The correction email uses the same result and the transcript average uses the penalized grades.

Keep the existing date-only deadline: the entire deadline day is allowed, and each subsequent calendar date adds one late day. I confirmed this interpretation before implementation. Calculate the grade as `max(rawPoints, 0) * 20 * (1 - 0.10 * min(lateDays, 5)) / maxPoints`, rounding only the final result to one decimal with `BigDecimal` and `HALF_UP`.

Keep raw points in the database; never save a penalized grade or compound penalties when grading again. Before saving correction points, round them to the database's existing two-decimal precision, so the immediate correction and later reads use the same value.

## Alternatives and consequences

Repeating the formula in every output would be a smaller local edit but would preserve the risk of inconsistent calculations. A database migration or full application rewrite is unnecessary.

Existing graded submissions receive the rule when read. CSV keeps its existing columns, semicolon separator and decimal comma. HTTP fields and routes remain unchanged. Existing French business messages and seed data remain unchanged; new code comments and documentation are in English.

The calculator assumes the positive exercise scale already required by exercise creation. Tests cover calculation boundaries and compare all output paths with mocked JDBC and mail dependencies. A live check against PostgreSQL and Mailpit on 2026-11-24 confirmed the same grades (16.0, 14.4, 11.2, 8.0) in correction, detail, transcript, CSV and email.
