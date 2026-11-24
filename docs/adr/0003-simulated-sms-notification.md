# ADR 0003 — Add a replaceable SMS sender

- Date: 2026-11-24
- Status: implemented

## Context

C2 adds an SMS notification when a teacher publishes a grade. Email must remain available, an SMS failure must not undo the published grade, and the future SMS provider must be replaceable without changing grading code. C1 is already pushed.

## Decision

Add the `SmsSender` interface with `send(phoneNumber, message)`. Inject it into the existing correction controller. `LoggingSmsSender` is the only current Spring implementation and logs `[SMS] <phone> : <message>` without making external requests. A future implementation can replace this bean without changing the grading controller or calculator.

Keep the existing database update and email flow. After sending the email, attempt an SMS to the profile's existing `phone` field using the same final penalized grade. Catch runtime exceptions only around SMS sending, log a warning and return the normal successful response. Do not introduce a transaction spanning database publication and notification delivery.

If the phone is null or blank, skip SMS and retain the email. Email failure behavior is unchanged. No real SMS credentials, provider dependency, schema migration, retry queue or delivery guarantee is introduced.

## Failure simulation

The development provider throws `IllegalStateException` when `kilasy.sms.simulate-failure=true`. This switch belongs to the provider, not the grading logic, and defaults to false. An HTTP correction should still return 200, the new raw points must be saved and subsequent detail/transcript/CSV reads must show the grade.

## Alternatives and consequences

Writing the SMS log directly inside the controller would couple grading to the simulated provider. The small interface isolates this dependency without rewriting the existing application. Delivery remains synchronous and best effort. Logs contain the simulated recipient and grade; this adapter is for local development.
