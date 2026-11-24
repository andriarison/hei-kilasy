# AI Journal — Kilasy

## 2026-11-24 — Local startup

### Requests and decisions

- The student supplied the C1 late-penalty assignment, then requested fixing application startup first.
- The student requested documenting every change and reading `../PROG5.txt`. The course notes emphasize maintainability, tests, history, C4 and ADRs.
- The student approved the initial startup plan using port 5433.
- After successful startup, the student explicitly requested keeping port 5432, stopping the existing host PostgreSQL instance, and writing project documentation in English. This supersedes the port 5433 decision.

### Diagnosis and assistant proposals

Docker inspection confirmed that the host PostgreSQL service occupied port 5432. Mailpit was already running. `mvn -o compile -DskipTests` succeeded, but the API was unavailable until the database conflict was resolved.

The assistant initially proposed port 5433, matching the JDBC URL, README troubleshooting instructions and an ADR. The approved workaround was implemented and verified. Following the user's correction, the Compose mapping and JDBC URL were restored to their original port 5432 values. No student preferences or decisions beyond those actually expressed are assumed.

### Files and reasons

| File | Change and reason |
| --- | --- |
| `compose.yaml` | Initially changed to `5433:5432`; restored to `5432:5432` at the user's request. |
| `src/main/resources/application.properties` | Initially changed JDBC to 5433; restored to 5432 to match Compose. |
| `README.md` | Document prerequisites, startup, checks, Mailpit and resolving the port conflict by stopping host PostgreSQL. |
| `docs/adr/0001-port-postgresql-local.md` | Record the current decision, superseded workaround and operational consequences. |
| `Journal_IA.md` | Record requests, proposals, changes and actual verification results in English. |

### Verification of the initial port 5433 workaround

- `docker compose up -d`: successful; PostgreSQL recreated and started.
- `docker compose ps`: PostgreSQL up on 5433; Mailpit healthy.
- `docker compose exec -T postgres pg_isready -U kilasy -d kilasy`: accepting connections.
- `mvn spring-boot:run`: Hikari connected; Tomcat started on 8080 at 17:28 Indian/Antananarivo. Maven used Java 23; no JDK change was needed.
- Mailpit at `http://localhost:8025`: HTTP 200.
- `GET http://localhost:8080/exercises`: HTTP 200 with five demo exercises after startup. An earlier request before startup completed could not connect.
- `git diff --check`: passed.

### Return to port 5432

The original port settings have been restored. `sudo -n systemctl stop postgresql` could not stop the host service because sudo requires a password. The final state and any further checks are recorded below; the successful port 5433 verification does not establish that port 5432 works.

C1 implementation, its C4 diagram, business tests, commit `C1` and push remain pending. The student's actual personal working time has not been provided and is not estimated by the assistant.

- `systemctl --no-ask-password stop postgresql`: access denied; interactive administrator authentication is required.
- Stopped the previously launched Kilasy Java process with SIGTERM and stopped its PostgreSQL container on 5433. Mailpit remains running.
- Restored Compose/JDBC configuration is on 5432; startup verification on this port remains pending until the user stops the host service.
- Documentation is now in English. Existing French business messages and demo data were not changed by this startup task.

## 2026-11-24 — C1 implementation

### Request and retained decisions

The student explicitly requested implementing C1, reporting the actual changes, and creating a commit with exactly `C1`, with no body or coauthor trailer. The assistant's shared-calculator proposal was implemented. Calendar-day counting was confirmed earlier by the student. New documentation and code comments are in English.

### Actual changes

| File | Reason |
| --- | --- |
| `src/main/java/hei/school/GradeCalculator.java` | Centralize calendar-day penalties, 50% cap, non-negative grade and final half-up rounding to one decimal. |
| `src/main/java/hei/school/SubmissionController.java` | Share the calculation in correction, detail and email; align correction points with the database's two-decimal storage precision before calculating the response. |
| `src/main/java/hei/school/GradeReport.java` | Use penalized grades in transcript lines and their average. |
| `src/main/java/hei/school/CsvExport.java` | Use the same calculation and replace inconsistent truncation with shared rounding; preserve CSV formatting. |
| `pom.xml` | Add Spring Boot's test starter for JUnit, Mockito and test utilities. |
| `src/test/java/hei/school/GradeCalculatorTest.java` | Thirteen calculation cases: deadline boundaries, examples, cap, zero, negative protection, different scales and rounding. |
| `src/test/java/hei/school/GradeConsistencyTest.java` | Six cross-output scenarios plus an ungraded scenario; compare correction, detail, transcript, average, CSV and email with mocked dependencies. Includes two-decimal persistence precision. |
| `README.md` | Describe C1 behavior, rounding and how to run tests, alongside earlier startup documentation. |
| `docs/adr/0002-shared-late-penalty.md` | Explain the shared-calculation decision and alternatives. |
| `docs/architecture.md` | Document the existing containers and shared calculation with a C4 container view. |
| `Journal_IA.md` | Record the actual implementation and its verification limits. |

No schema migration, route change or port change was made for C1. Raw points remain stored separately from the calculated penalty. Existing French business text was retained.

### Verification and limits

- Final `mvn -B test`: BUILD SUCCESS, 20 tests, zero failures/errors/skips (17:41 Indian/Antananarivo).
- For the same mocked submission, all output paths give 16.0 on time, 14.4 the next day, 11.2 three days late and 8.0 eight days late. Rounding cases give 12.0 for 13.37 raw points and 12.1 when 13.388 is stored as 13.39.
- `git diff --check`: passed.
- Live HTTP/PostgreSQL checks for C1 were not performed: port 5432 is still occupied by the host service, and `sudo -n systemctl stop postgresql` requires a password. Tests exercise application methods with mocked JDBC/mail, not an actual database or HTTP server.
- GitHub identifies `andriarison/hei-kilasy` as a fork of `hei-ryan/hei-kilasy`; the connected CLI account reports READ permission. Push may therefore fail.
- Personal student time is unknown. No claim is made about the student's personal acceptance or rejection of implementation details beyond their expressed instructions.

### Delivery

Created a commit with subject `C1`, no body and no coauthor trailer. `git push origin main` failed with HTTP 403: GitHub denied write access to `teamhackify12-creator` on `andriarison/hei-kilasy`. The implementation is committed locally; pushing requires an account with write access to this fork. The failure is documented in this same C1 commit.

## 2026-11-24 — Live application verification on port 5432

The student stopped the host PostgreSQL service. `docker compose up -d` started the database, but its previously failed container had no active host port mapping. A restart alone did not restore it; `docker compose up -d --force-recreate postgres` restored `5432:5432`. Existing database data was preserved. No source configuration or port change was needed.

`mvn spring-boot:run` then started successfully: Hikari connected and Tomcat listened on 8080. The initial failed attempt before recreating the container reported a refused database connection.

Live HTTP checks against the actual PostgreSQL database passed:

| Submission | Scenario | Correction | Detail | Transcript | CSV |
| --- | --- | --- | --- | --- | --- |
| 1 | On time | 16.0 | 16.0 | 16.0 | 16,0 |
| 2 | Next day, 02:00 | 14.4 | 14.4 | 14.4 | 14,4 |
| 3 | Three days late | 11.2 | 11.2 | 11.2 | 11,2 |
| 4 | Eight days late | 8.0 | 8.0 | 8.0 | 8,0 |

- `GET /exercises`: HTTP 200, five exercises.
- Regraded submissions 1–4 through `PUT /submissions/{id}/grade`, preserving their current raw points and feedback. This updates their grading timestamps and creates four local Mailpit emails.
- Compared each correction response with `GET /submissions/{id}`, the matching transcript line, and the matching CSV row; all matched the expected grade.
- Verified transcript averages against the returned final grades.
- Submitting negative points returned HTTP 400.
- The first ungraded assertion used submission 5 and failed because it was already graded with 15 points in the existing database. No change was made to it. Verified instead that submissions 7 and 12 remain ungraded.
- Mailpit API returned HTTP 200; the four newest captured emails contain 8.0/20, 11.2/20, 14.4/20 and 16.0/20 as expected.
- Earlier automated rerun: 20 tests passed with no failures/errors/skips.

The previous live-test blocker is resolved. Kilasy, PostgreSQL on 5432 and Mailpit are left running. This verification changes no business code. The earlier C1 commit was successfully pushed using the student's `andriarison` account; this journal update records subsequent verification.
