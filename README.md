# job-tracker

A job-hunt tracker with a small **rules engine**: when something happens to an application
(stage change, going stale), user-defined rules decide what to do (create a checklist, set a reminder, archive).
Built to show reliable event handling, not CRUD.

## Run it

```bash
docker compose up -d postgres          # database only
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# or everything in containers:
docker compose --profile app up --build
```

Try the requests in `docs/api-examples.http`.

Tests: `mvn test` (unit) and `mvn verify` (adds the Testcontainers integration tests, needs Docker).

## Architecture

```
 REST ──► ApplicationService ──┬─► applications  (aggregate + state machine)
                               ├─► audit_logs    (append-only)
                               └─► outbox_events (same transaction)
                                         │
                      OutboxPoller (FOR UPDATE SKIP LOCKED, at-least-once)
                                         ▼
                                    RuleEngine ── match trigger + conditions
                                         │  JobQClient.enqueue(idempotency key)
                                         ▼
                                  rule_executions  (UNIQUE idempotency_key)
                                         │
                      JobWorker (claim → run + mark done atomically → retry/backoff)
                                         ▼
                      ActionRegistry ──► ActionHandler (checklist | reminder | archive)

 StaleApplicationScheduler ──► outbox_events ("application.stale")
```

## Patterns and why

| Pattern | Where | Why |
|---|---|---|
| State machine | `Stage`, `Application.moveTo` | Illegal pipeline moves are impossible, not just validated in the controller |
| Transactional outbox | `OutboxPublisher` (`MANDATORY` propagation) | State change, audit row and event commit together or not at all |
| Competing consumers | `lockPendingBatch`, `RuleExecutionRepository.claim` | `SKIP LOCKED` lets several instances poll without double-processing |
| Idempotent consumer | `UNIQUE(idempotency_key)` + `ON CONFLICT DO NOTHING` | Redelivered events produce one job; see `IdempotencyIT` |
| Strategy + registry | `ActionHandler`, `ActionRegistry` | New action = one new class, no engine changes |
| Port/adapter seam | `JobQClient` | Swap the in-DB queue for the real JobQ without touching the rules code |
| Optimistic locking | `@Version` on `Application` | Concurrent edits get a 409 instead of silently overwriting |
| Problem Details | `GlobalExceptionHandler` | Consistent RFC 7807 error bodies |

## Trade-offs

- **At-least-once, not exactly-once.** Handlers run in the same transaction as the "succeeded" flag, so side
  effects are effectively once, but an external call (email, HTTP) inside a handler would need its own idempotency key.
- **Polling, not push.** Simple and robust; latency is the poll interval (default 1s). Postgres `LISTEN/NOTIFY`
  would cut that if it ever mattered.
- **Outbox ordering is best-effort** (`created_at`). Rules must not depend on strict cross-aggregate ordering.
- **Stuck jobs** are recovered by lock expiry (`tracker.jobs.lock-timeout-seconds`); a job that keeps crashing
  workers ends up `FAILED` once attempts run out.
- **Rule conditions are AND-only** with four operators (`eq`, `in`, `gt`, `contains`). Enough for the use case; OR/NOT would be a new `Condition` type.
- Accounts are email + password with email verification and emailed password reset (see "Accounts" below). No social login or 2FA.

## Frontend

`frontend/` is a React + TypeScript app (Vite). Kanban board with drag-and-drop stage changes, an application
drawer (history, checklists, reminders), a rule builder, and a reminders list.

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173, proxies /api to localhost:8080
```

The UI never re-implements business rules: the board offers whatever `allowedNext` the API returns, and an
illegal drag simply shows the server's 409 message.

## Notifications

Every time a rule's action succeeds, the app records a notification (Notifications tab). If the user opted in
(email address + checkbox in the Notifications tab), the same notification is also emailed.

- The notification is written in the **same transaction** as the action, so it exists if and only if the action committed.
- Emails are **not** sent inside that transaction. They are queued (`email_status = PENDING`) and delivered by
  `NotificationEmailSender` (`SKIP LOCKED` batch, retry up to 3 times, then `FAILED` with the error shown in the UI).
  Delivery is at-least-once: a crash right after SMTP accepts a message can send it twice.
- The recipient is snapshotted when the notification is created, so changing the address never redirects queued mail.

Try it locally without a real mail account:

```bash
docker compose up -d postgres mailpit
mvn spring-boot:run -Dspring-boot.run.profiles=dev     # dev profile points SMTP at Mailpit
# open http://localhost:8025 to read the emails
```

Real SMTP (for example Gmail with an app password), without the dev profile:

```bash
SPRING_MAIL_HOST=smtp.gmail.com SPRING_MAIL_PORT=587 \
SPRING_MAIL_USERNAME=you@gmail.com SPRING_MAIL_PASSWORD=<app password> \
mvn spring-boot:run
```

Without any SMTP host, in-app notifications still work and emails are marked `FAILED` with a clear message.

## Follow-up emails to HR / careers

A rule can send a templated email to the HR or careers address saved on an application.

1. Save the address on the application (new-application form, or the detail panel).
2. Pick or write a template in the **Templates** tab. `{{company}}`, `{{role}}`, `{{stage}}`, `{{daysIdle}}`,
   `{{appliedDate}}` and `{{jobUrl}}` are filled in automatically; any other `{{name}}` is a value you set when you
   create the rule. Built-in templates can be copied; your own can be edited and deleted.
3. In **Rules**, use "Use the 7-day follow-up example" (or build it by hand):
   *When application goes stale, if stage equals APPLIED and daysIdle at least 7, send email (template)*.

How it runs: `StaleApplicationScheduler` emits `application.stale` (with `daysIdle`) once a day for idle Applied or
Screening applications. The rule engine turns a match into a job, the worker renders the template and **queues** an
`outgoing_emails` row, and `OutgoingEmailSender` delivers it (retries, then FAILED). You get an in-app notification
when it is sent or finally fails.

Safety properties:
- **Once per idle stretch.** The stale event carries a `dedupeKey` (application + start of the idle stretch), so the
  rule's job key is the same every day: one email, not one per day. A stage change starts a new stretch.
- **No address yet?** The job is parked as `SKIPPED` and you get one notification. After you save the address, the next
  daily event revives it and the email is queued.
- **Rules can't send half-filled emails.** The rule form requires a value for every user placeholder, and a template that
  later gains a new placeholder fails with a notification instead of sending `{{like this}}`.
- A template used by a rule can't be deleted.

To try it without waiting a week (dev profile runs the daily check every minute):

```sql
UPDATE applications SET last_activity_at = now() - interval '8 days' WHERE company = 'Acme';
```

## Accounts (multi-user)

Every user has their own account and sees only their own applications, rules, templates, reminders, notifications
and sent emails.

- **Login:** `POST /api/auth/register`, `/login`, `/logout`, `GET /api/auth/me`. Passwords are hashed with BCrypt; the
  login is a session cookie (`JSESSIONID`, HttpOnly, SameSite=Lax). Sessions live in memory, so a backend restart logs
  everyone out. Set `SESSION_COOKIE_SECURE=true` when serving over HTTPS.
- **CSRF:** protection stays on. The server sets an `XSRF-TOKEN` cookie and the React app echoes it in the
  `X-XSRF-TOKEN` header on every write.
- **Brute force:** 5 wrong passwords for one email locks that email for 15 minutes (in memory, single instance).
- **Ownership:** every user-owned table has a `user_id`, and every query filters on it. Loading another user's record
  returns 404, the same as a record that doesn't exist. Built-in email templates are the one shared thing.
- **Background jobs:** the owner travels with each event and job (`outbox_events.user_id`, `rule_executions.user_id`).
  The rule engine only loads the event owner's rules, and the daily stale check publishes each event under that
  application's owner. `TenantIsolationIT` proves a user's rule never fires on someone else's data.
- **Email sending:** all follow-ups still go out through the app's one SMTP account, but each message gets the user's
  own login email as **Reply-To**, so HR's reply reaches the user.
- **Existing data:** migration `V8` assigns rows created before accounts existed to a placeholder owner. The **first
  account you register takes them over** automatically, and the placeholder is deleted.

Calling the API without the UI needs the cookie and CSRF token:

```bash
curl -c jar.txt http://localhost:8080/api/auth/me          # 401, but sets the XSRF-TOKEN cookie
TOKEN=$(grep XSRF-TOKEN jar.txt | awk '{print $7}')
curl -b jar.txt -c jar.txt -H "X-XSRF-TOKEN: $TOKEN" -H "Content-Type: application/json" \
  -d '{"email":"me@example.com","password":"correct-horse-1"}' http://localhost:8080/api/auth/register
curl -b jar.txt http://localhost:8080/api/applications
```

### Email verification and password reset

- **Verification:** registering creates an *unverified* account and emails a link. There is **no session until the link is
  opened**, so signing up with someone else's address gets you nothing (and it no longer lets you send HR emails with
  their address as Reply-To). Logging in before verifying returns `403 EMAIL_NOT_VERIFIED`; the login page then offers
  "Resend verification email". Links expire after 24 hours.
- **Password reset:** "Forgot password?" emails a link that expires in 1 hour and works once. Resetting also **logs out
  every existing session** (`users.session_version` is bumped; `SessionVersionFilter` rejects older sessions) and marks
  the email verified, since the user just proved they own the mailbox.
- **Tokens:** 256-bit random, delivered only in the email link. The database stores a SHA-256 hash, so reading the
  database doesn't give anyone a usable link. Using a token is one atomic `UPDATE ... RETURNING`, so a link can't be used
  twice, even by two simultaneous requests. A new link of a type replaces the old one.
- **The link carries the token in the URL fragment** (`/#verify=...`, `/#reset=...`), which browsers never send to servers
  or in Referer headers. The app removes it from the address bar after use.
- **No account enumeration on the email forms:** "forgot password" and "resend" return 202 whether or not the address has
  an account, and are limited to 3 per address per hour. (Registration still says an email is taken, a deliberate trade-off.)
- **Delivery:** account emails go through the same queue as every other email (`outgoing_emails`, `kind = ACCOUNT`), so a
  slow or down mail server never blocks a request, and failures are retried.
- **Existing accounts** (created before this feature) are marked verified by migration `V9`, so nobody is locked out.
  Pre-accounts data is now claimed by the first account that is *verified*, not just registered.

Configuration (`application.yml`): `tracker.auth.require-email-verification` (set `false` to log in straight after
registering, no mail needed), `verification-ttl-hours`, `reset-ttl-minutes`, `max-emails-per-hour`, and
`tracker.app.base-url` / `APP_BASE_URL` (where the email links point; the React app's address).

Trying it locally: run Mailpit (`docker compose up -d mailpit`) and open http://localhost:8025 to read the emails. The dev
profile also prints each verification / reset link in the backend log (`tracker.auth.log-emails`), so it works even without
Mailpit. Never enable `log-emails` in production, because those links are credentials.

## Funnel analytics (Insights tab)

`GET /api/analytics/funnel?weeks=12` returns, for the logged-in user only: applications sent, response rate, median wait for a
first reply, a five-step funnel (Applied, Screening, Interview, Offer, Accepted) with step-to-step conversion and median days
from applying, where rejections happened, where everything sits right now, and applications sent per week.

How it works: nothing new is stored. The numbers are rebuilt from the audit trail (`CREATED` and `STAGE_CHANGED` rows), which
already records every stage move with a timestamp. `FunnelCalculator` is a pure function (no database, no clock, no Spring), so
every figure is covered by a plain unit test (`FunnelCalculatorTest`).

Definitions, also shown on the page: an application "reached" a stage if it was ever in it (moving on or archiving later doesn't
undo that), and reaching a later stage counts for earlier ones too, because stages can be skipped. "Heard back" means it reached
Screening or later, or was rejected after applying. Times are medians. Weeks start on Monday (UTC).

## Editing an application

`PUT /api/applications/{id}` edits company, role, job link and notes (stage and contact email keep their own endpoints).

- It never touches the stage or `last_activity_at`, so fixing a typo can't reset the idle clock that follow-up rules use.
- It writes one `DETAILS_UPDATED` audit row listing what changed, and nothing at all if nothing changed.
- Job links must start with `http://` or `https://` (also enforced on create). The UI renders the link as clickable, so a
  `javascript:` URL must never be stored.
