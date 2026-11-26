# Kilasy architecture — C1, C2 and C3

This C4 container view documents the existing application. C1 adds a shared calculation inside the Spring Boot application; it introduces no new service or database schema.

```mermaid
flowchart LR
    teacher["Teacher (person)"] -->|"HTTP: exercises and correction"| app["Kilasy (container: Java / Spring Boot)"]
    student["Student (person)"] -->|"HTTP: start timed exercise, submissions and transcript"| app
    registrar["Registrar (person)"] -->|"HTTP: CSV export"| app
    app -->|"JDBC: raw points, submission dates and countdown starts"| db[("PostgreSQL (container)")]
    app -->|"SMTP: grade notification"| mail["Mailpit (external test mail system)"]
    app -->|"Simulated SMS: profile phone and final grade"| logs["Application logs (local output)"]
```

Inside Kilasy, `SubmissionController`, `GradeReport` and `CsvExport` call `GradeCalculator`. The same final grade reaches the correction response, submission detail, email, transcript, average and CSV. PostgreSQL keeps raw points and dates; the penalty is calculated on each read. Mailpit captures development emails for inspection at `http://localhost:8025`.

See [ADR 0002](adr/0002-shared-late-penalty.md) for the calculation decision and [ADR 0001](adr/0001-port-postgresql-local.md) for the local port decision.

C2 injects the `SmsSender` interface into `SubmissionController`. Its development implementation, `LoggingSmsSender`, writes to the application logs. Provider errors are caught after publication, so the saved grade remains available. The provider owns the optional outage simulation switch. See [ADR 0003](adr/0003-simulated-sms-notification.md).

C3 adds no container. `ExerciseController` exposes `POST /exercises/{id}/start` and records the start in the `exercise_start` table; `SubmissionController` checks it with `ExerciseTimer` before accepting a submission to a timed exercise. See [ADR 0004](adr/0004-timed-exercises.md).
