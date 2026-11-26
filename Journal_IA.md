# AI Journal — Kilasy

Tool used: Claude Code (AI coding assistant), in the terminal.

Each exchange follows the course format: request, proposal, decision (kept, kept with a correction, or refused), why, and verification.

## Change: local startup (before C1)

### Exchange 1 — 2026-11-24
Request: « Fix the application startup first, read `../PROG5.txt`, and document every change. »
Proposal: the API was unreachable because the host PostgreSQL service already used port 5432. The assistant proposed publishing the Kilasy container on 5433 and changing the JDBC URL to match.
Decision: kept at first, then replaced (see exchange 2).
Why: it was the quickest way to get the application running without touching the host database.
Verification: `docker compose up -d`, `pg_isready` accepting connections, Spring Boot started on 8080, `GET /exercises` returned the five demo exercises, Mailpit answered on 8025.

### Exchange 2 — 2026-11-24
Request: « Keep port 5432, stop the host PostgreSQL instead, and write the documentation in English. »
Proposal: restore `5432:5432` in `compose.yaml` and the original JDBC URL, document the conflict in the README and in ADR 0001.
Decision: kept.
Why: the 5433 workaround changed shared project files to fix a problem that only exists on my machine. Everyone else uses 5432.
Verification: after I stopped the host service, `docker compose up -d --force-recreate postgres` was needed (a plain restart kept the old failed container without its port mapping). Spring Boot then connected to PostgreSQL on 5432.

## Change: C1 — late submission penalty

### Exchange 3 — 2026-11-24
Request: the C1 assignment (10% per late day, capped at 50%).
Proposal: count calendar days after the deadline date: the whole deadline day is allowed, 00:00 the next day is one day late.
Decision: kept, after I confirmed this interpretation.
Why: the deadline column is a `DATE` with no time, and the existing "late" rule already compares dates.
Verification: calculator tests for 23:59:59 on the deadline day (16.0), 00:00:00 the next day (14.4), three days (11.2), eight days (8.0, cap).

### Exchange 4 — 2026-11-24
Request: « Implement C1. »
Proposal: one `GradeCalculator` used by correction, submission detail, email, transcript and CSV, instead of adding the penalty to each place.
Decision: kept.
Why: before C1, the grade was computed three times with three different roundings (CSV truncated with `Math.floor`, so Emma got 13.7 in the CSV and 13.8 elsewhere). Adding the penalty in three copies would have kept that inconsistency.
Verification: `mvn test` (20 tests at the time) and a cross-output test that compares every output for the same submission.

### Exchange 5 — 2026-11-24
Request: none; the assistant raised it while writing the C1 tests: the correction response and later reads could differ for points with more than two decimals.
Proposal: round the points to the database precision (`NUMERIC(5, 2)`) before saving and before calculating the returned grade.
Decision: kept.
Why: without it, 13.388 points returned 12.0 immediately but 12.1 when read back (stored as 13.39).
Verification: consistency test case `13.388 → 12.1` on every output.

### Exchange 6 — 2026-11-24
Request: verify C1 on the real database, not only with mocks.
Proposal: regrade submissions 1 to 4 with their existing points and compare correction, detail, transcript, CSV and Mailpit.
Decision: kept, with a correction.
Why: the planned "ungraded" check used submission 5, but in my local database it had already been graded (15 points). I did not change it and used submissions 7 and 12 instead.
Verification: 16.0 / 14.4 / 11.2 / 8.0 identical in all five outputs; negative points rejected with 400; submissions 7 and 12 still ungraded.

## Change: C2 — simulated SMS notification

### Exchange 7 — 2026-11-24
Request: the C2 assignment (SMS in addition to the email, a failed SMS must not undo the grade, provider replaceable).
Proposal: an `SmsSender` interface with one implementation, `LoggingSmsSender`, that writes `[SMS] <phone> : <message>` to the logs. The controller calls it after the email and catches its exceptions.
Decision: kept.
Why: writing the log line directly in the controller would tie grading code to the fake provider. The interface is the smallest change that makes the provider replaceable without rewriting the controller.
Verification: tests for the normal SMS, missing or blank phone (no SMS, email still sent), invalid grade (no notification).

### Exchange 8 — 2026-11-24
Request: how do I show that an SMS failure does not cancel the grade?
Proposal: a development switch `kilasy.sms.simulate-failure`, owned by the fake provider and `false` by default.
Decision: kept.
Why: it lets me reproduce an outage on the running application without changing code.
Verification: with the switch on, `PUT /submissions/2/grade` returned 200, PostgreSQL held `raw_points = 15.00`, detail/transcript/CSV/email showed 13.5 and the log showed `SMS notification failed`. I then restored 16 points (14.4) with the switch off. Port 8080 was taken by another local application at that moment, so this last check ran on 8081 with a command-line override; the project default stays 8080.

## Summary

- Files changed: `GradeCalculator`, `SmsSender`, `LoggingSmsSender` (new); `SubmissionController`, `GradeReport`, `CsvExport`, `application.properties`, `pom.xml` (test starter); three test classes; README, `docs/architecture.md`, ADRs 0001–0003.
- Not changed: no database migration, no new route for C1/C2, French business messages and demo data unchanged.
- Known limits (recorded for the code review): the SMS text is in English while the email is in French; the simulated SMS writes the phone number and grade to the logs; if the email fails the API returns 500 after saving the grade and the SMS is not attempted; grading twice sends the email and SMS again.
- Automated tests: 26 passing after C2 (`mvn test`).
- Personal working time: _to be completed by me_.
