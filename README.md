# Kilasy

Kilasy is a programming exercise platform. Teachers publish exercises with a deadline, students submit their code or a link to their GitHub repository, and teachers then grade them. Students receive their grade by email.

Production version: 1.3.0.

## Stack

- Java 21, Spring Boot 4 (Web MVC, JDBC, Mail)
- PostgreSQL 17
- Maven

## Getting started

Pre-requisites: JDK 21, Maven and Docker with Docker Compose (see the installation guide on hei-lms.app). The Docker daemon must be running. Check your tools with `java -version`, `mvn -version` and `docker compose version`.

```bash
docker compose up -d
mvn spring-boot:run
```

The API responds on http://localhost:8080.

`docker compose` starts two services:

- PostgreSQL on host port 5432 (also port 5432 inside the container);
- Mailpit, a fake mail server: emails sent by Kilasy are displayed at http://localhost:8025.

Spring Boot connects to `jdbc:postgresql://localhost:5432/kilasy`. Mailpit receives application emails on SMTP port 1025. Run Maven in a terminal you leave open; stop the application with Ctrl+C.

Check the running services and API:

```bash
docker compose ps
docker compose exec -T postgres pg_isready -U kilasy -d kilasy
curl http://localhost:8080/exercises
```

If Compose reports `address already in use` on port 5432, another PostgreSQL instance may already be running on the host. Inspect ports on Linux with `ss -ltnp` and check `systemctl status postgresql`. To keep Kilasy on port 5432, stop the host service with `sudo systemctl stop postgresql`, then retry `docker compose up -d`. This stops the host database until it is restarted (or the machine reboots); it does not delete its data. To use the host service again, stop the Kilasy PostgreSQL container first, then run `sudo systemctl start postgresql`.

If Maven fails to connect to the database, check `docker compose ps -a` and `docker compose logs postgres`, then ensure PostgreSQL is ready before retrying `mvn spring-boot:run`.

The local port decision is documented in [ADR 0001](docs/adr/0001-port-postgresql-local.md); changes and verification results are recorded in [Journal_IA.md](Journal_IA.md).

**The SQL scripts are idempotent**: at every startup the schema and the demo data are created only if missing, and existing data is kept. To start from scratch, run `docker compose down` (PostgreSQL has no persistent volume, so removing the container wipes the data). The demo data is: 2 teachers, 6 students, 5 exercises and 12 submissions. After a change to `schema.sql` (for example C3), recreate the database with `docker compose down` then `docker compose up -d`: `CREATE TABLE IF NOT EXISTS` does not modify an existing table.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/exercises` | List exercises |
| GET | `/exercises/{id}` | Exercise details |
| POST | `/exercises` | Create an exercise (teacher) |
| POST | `/exercises/{id}/submissions` | Submit an exercise (student) |
| GET | `/submissions/{id}` | Submission details |
| PUT | `/submissions/{id}/grade` | Grade a submission (teacher) |
| GET | `/students` | List students |
| GET | `/students/{id}/transcript` | A student's grade transcript |
| GET | `/exports/grades.csv` | Grades export for the registrar's office |
| POST | `/exercises/{id}/start` | Start the student's countdown for a timed exercise (C3) |

There is no authentication yet: the student or teacher identifier is passed in the request body.

## Examples

Create an exercise:

```bash
curl -X POST http://localhost:8080/exercises \
  -H "Content-Type: application/json" \
  -d '{"title": "Recherche dichotomique", "maxPoints": 20, "deadline": "2027-03-01", "teacherId": 1}'
```

Submit an exercise:

```bash
curl -X POST http://localhost:8080/exercises/4/submissions \
  -H "Content-Type: application/json" \
  -d '{"studentId": 2, "repoUrl": "https://github.com/bob/reservation-api"}'
```

Grade a submission, then check the email received at http://localhost:8025:

```bash
curl -X PUT http://localhost:8080/submissions/5/grade \
  -H "Content-Type: application/json" \
  -d '{"points": 15, "feedback": "Bien, mais commente ton code."}'
```

View a grade transcript and the export:

```bash
curl http://localhost:8080/students/1/transcript
curl http://localhost:8080/exports/grades.csv
```

## Business rules

- A submission contains code, a link to a GitHub repository, or both.
- A student can only submit once per exercise.
- A submission sent after the deadline day is marked as late.
- Points are entered on the exercise's scale (10, 20, 40…) and converted to a grade out of 20.

## Late submission penalty (C1)

The whole deadline day is allowed. Each calendar day after the deadline deducts 10% of the grade, capped at 50%. For raw points of 16/20: on time gives 16.0, the next day gives 14.4, three days late gives 11.2, and eight days late gives 8.0.

Correction, submission details, notification emails, transcripts and CSV use one calculation. The final grade is rounded once to one decimal (half up); the transcript average uses these final grades. Raw points are preserved in the database at its existing two-decimal precision. Ungraded submissions remain ungraded. No database migration is needed.

Run the automated checks with `mvn test`. Calculation tests cover deadline boundaries, the cap, zero, negative input protection, other scales and rounding. Output consistency tests invoke the existing correction, detail, report and CSV methods using mocked JDBC and email dependencies; they are not live HTTP/database tests.

See the [architecture view](docs/architecture.md) and [calculation decision](docs/adr/0002-shared-late-penalty.md).

## SMS notification (C2)

Publishing a grade still sends the existing email and now also logs a simulated SMS to the phone number on the student's profile:

```text
[SMS] +261 34 00 000 02 : Your grade for « Tri à bulles » is available: 14.4/20.
```

No real SMS is sent. Missing or blank phone numbers skip SMS. An SMS failure is logged and does not cancel the saved grade or its successful HTTP response. Email behavior is unchanged.

To simulate a provider outage locally, stop the running application and launch:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--kilasy.sms.simulate-failure=true
```

Grade a submission using the existing PUT route, then check the response, the submission detail, transcript and CSV. The logs should contain `SMS notification failed` while the grade remains published and the email is visible in Mailpit. Restart with the usual `mvn spring-boot:run` to restore normal simulation.

`SmsSender` is the provider boundary; replacing `LoggingSmsSender` with another Spring implementation does not require changing grading code. Run `mvn test` for C1 regression checks, normal SMS verification, missing-phone behavior and a simulated provider exception. See [ADR 0003](docs/adr/0003-simulated-sms-notification.md).

## Timed exercises (C3)

An exercise can have a duration in minutes (`durationMinutes`), in addition to or instead of its deadline. The student starts the countdown, then must submit before it ends:

```bash
curl -X POST http://localhost:8080/exercises \
  -H "Content-Type: application/json" \
  -d '{"title": "Palindrome", "maxPoints": 20, "durationMinutes": 30, "teacherId": 1}'
curl -X POST http://localhost:8080/exercises/6/start \
  -H "Content-Type: application/json" -d '{"studentId": 2}'
curl -X POST http://localhost:8080/exercises/6/submissions \
  -H "Content-Type: application/json" -d '{"studentId": 2, "content": "class Palindrome {}"}'
```

- `/start` returns 201 with `startedAt` and `endsAt`; calling it again returns 200 with the same times (the countdown is never reset). It returns 422 for an untimed exercise and 409 if the student already submitted.
- A timed submission is refused with HTTP 422 if the exercise was not started, or after `endsAt` (a submission at exactly `endsAt` is accepted).
- The transcript (`GET /students/{id}/transcript`) shows the time used on each timed exercise (`timeUsed`, for example `12 min 05 s`, and `timeUsedSeconds`). A started exercise whose time ran out without a submission is `NON RENDU`.
- Exercises without a duration behave as before. The late penalty still depends only on the deadline, so a timed-only exercise is never late.

See [ADR 0004](docs/adr/0004-timed-exercises.md).
