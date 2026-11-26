# ADR 0004 — Timed exercises with a per-student countdown

- Date: 2026-11-26
- Status: implemented

## Context

C3: a teacher can give an exercise a duration in minutes, in addition to or instead of its deadline. The countdown starts when the student opens the exercise through `POST /exercises/{id}/start`, and a submission sent after the duration ends is refused with HTTP 422 and an explicit message.

## Decision

- `exercise.duration_minutes` (nullable, positive). `exercise.deadline` becomes nullable. A database `CHECK` requires a deadline, a duration, or both; the creation endpoint validates the same rule with a French error message.
- A new table `exercise_start (exercise_id, student_id, started_at)` with a primary key on the pair. The start time is a fact about the student and the exercise, not about the submission, so it gets its own table instead of a column on `submission` (a submission does not exist yet when the countdown starts).
- `POST /exercises/{id}/start` with `{"studentId": …}` returns 201 on the first start and 200 with the **same** `startedAt` afterwards. The insert uses `ON CONFLICT DO NOTHING`, so repeated or concurrent calls never reset the countdown. It returns 422 for an untimed exercise, 404 for an unknown exercise or student, and 409 if the student has already submitted.
- `ExerciseTimer` holds the rule: `endsAt = startedAt + duration`; a submission at exactly `endsAt` is accepted, one instant later is refused.
- Submitting to a timed exercise without a start returns 422, because otherwise the time limit could be bypassed by never calling `/start`.
- The late penalty (ADR 0002) still depends only on the deadline. A timed-only exercise has no deadline, so no penalty; with both, the duration controls acceptance and the deadline controls the penalty.
- The transcript shows, for each timed exercise, `startedAt`, `timeUsed` (for example `12 min 05 s`) and `timeUsedSeconds`: from the start to the submission, or to now while the countdown runs, never more than the duration. Not started gives `timeUsed: null`. A started exercise whose countdown ended without a submission is `NON RENDU`.
- The current time comes from a `Clock` field in the two controllers and in `GradeReport`, so tests use a fixed instant.

## Alternatives

- Store `started_at` on `submission` with a "draft" row: it would mix two lifecycles and break the existing rule "one submission per student", which currently means a real submission.
- Start the countdown on `GET /exercises/{id}`: a GET must not change state, and the assignment defines a dedicated POST entry point.
- Migrate with `ALTER TABLE` statements: the course setup recreates the database (`docker compose down` then `up -d`), and the scripts remain plain `CREATE TABLE IF NOT EXISTS`. An existing database must be recreated to receive the new columns.

## Consequences and limits

- No authentication yet: `studentId` is in the body, as for submissions, so a student could start or submit on behalf of someone else.
- Times are server local time (`TIMESTAMP` without time zone), like `submitted_at`.
- Starting is allowed after the deadline of an exercise that has both; the late penalty then applies.
