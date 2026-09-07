# First working slice: room-only meeting

This section records the original slice. The second slice now adds real workshop specialists, quotes and multi-resource booking; see [workshop-slice.md](workshop-slice.md) for its current contracts and validation record. The standard Maven build includes the frontend without a profile.

Implement the real React → REST → Loomspan → Java services → Hibernate/H2 journey before adding workshop services. This slice uses one room and a fixed 13:00–18:00 event block, with reservations covering 12:30–18:30 in America/Los_Angeles. The user supplies an explicit event date, attendance, title, and USD budget. Catering, technical services, credits, and arbitrary schedules are not accepted inputs yet.

## Stories and acceptance

1. Start a standalone Spring application. Flyway creates and seeds file-backed H2; Hibernate validates it. Restarts preserve records. Cedar's seeded morning reservation ends exactly at the requested block's start.
2. Submit confirmed requirements from React. Reject missing/invalid dates, attendance, titles and budgets before any model invocation. Persist an immutable request so tool arguments cannot alter its constraints.
3. Assess through real `SkillTemplate`. `assessEvent` delegates to `planEventSpace`, whose Java children retrieve room facts and check candidate availability. The root obtains a Java quote. Save the assessment, selected proposal, supporting room/version, and execution reference. Model failures become failed assessments, never fabricated proposals.
4. Display a validated Cedar proposal at $300 for 20 attendees and a $500 budget. Java recalculates the candidate against the stored request and rejects fabricated IDs, prices, over-capacity, over-budget, or unavailable selections.
5. Accept a proposal using its server-issued ID. Lock its assessment, then its room; recheck availability, version and price; write one booking/reservation atomically. Repeated acceptance returns the existing booking. Two competing assessments cannot both book Cedar.
6. Display reservations and durable assessments after refresh. Show useful infeasibility, model failure, and HTTP 409 conflict states. Restart is not reset.

## HTTP contracts

- `GET /api/venue`: venue, fixed schedule policy, rooms and existing reservations.
- `POST /api/events`: `{title, eventDate, attendees, budgetCents}` → immutable request.
- `GET /api/events`: recent requests with their assessments/proposals.
- `POST /api/events/{id}/assessments`: runs a fresh synchronous bounded mission; returns the persisted assessment or an actionable error. No booking transaction spans the model call.
- `POST /api/proposals/{id}/accept`: accepts only the server-stored proposal; no client-provided price/room/role. Returns the existing booking when already accepted.

## Skill contracts

- `assessEvent(eventId)` → `{roomId, totalCents, summary, openQuestions}`; room and total nullable for no option. Output schema is closed; supporting Java quote is required for a quoted result.
- `planEventSpace(eventId)` → `{roomId, rationale}`; room nullable when no supported option meets the request.
- `listVenueRooms(eventId)` → request constraints plus catalog candidates.
- `checkRoomAvailability(eventId, roomId)` → capacity, price, buffered availability and violations.
- `validateRoomQuote(eventId, roomId)` → deterministic validation and total; nullable room means no candidate, not permission to invent one.

Business IDs travel explicitly. Java reads persisted requirements; model-controlled arguments cannot change them. Skill evidence supports direct-child claims and does not replace per-candidate application validation. Booking remains an application action.

## Validation

Tests cover migration/context startup, Java skill registration, input rejection, boundary intervals, quote tampering, infeasibility, failed assessments, idempotent acceptance, changed prices, and concurrent booking conflicts. A separate opt-in live smoke test exercises both YAML planners through a configured model and verifies the Cedar result and public execution observations. Model calls are absent from ordinary automated tests.

The frontend is a real Vite application in `frontend/`; `prototype/` remains a separately hosted reference. The standard Maven build installs frontend dependencies, builds the UI, and packages its assets into the Spring JAR without a separate profile. Keep model secrets and local database files out of source control.

## Implementation and validation record

Implemented the room-only slice with two YAML planners, three Java skills, five JPA repositories, Flyway schema/fixtures, persisted requests/assessments/proposals/bookings, and a React REST client. The packaged JAR includes the frontend; it binds to loopback and has no authentication yet.

Validated against framework commit `d202b204ea41a9cfee0364221888df157b469bc3`, starter `0.1.0-SNAPSHOT`, Java 21, Spring Boot 4.1.0, and the OpenAI `gpt-4.1` model configuration. Ordinary tests: 10 passed. A separate live test passed through both YAML planners, Java calls, and booking. The frontend TypeScript/production build and Maven frontend-packaging profile passed.

Packaged HTTP verification also created, assessed and booked a 20-person request on October 16 at $300, leaving the central October 15 fixture available. Earlier HTTP runs exposed a model contradiction: it described a Java-validated room as unavailable. Application validation rejected those outputs without a proposal. The space prompt now explicitly preserves valid candidates across subsequent checks and distinguishes actual tool facts from planned outcomes. The final HTTP run passed; this is a tested configuration, not a guarantee of identical model behavior on every invocation.

The development database may contain this verification request and its failed/successful assessment history. They are ignored local data, not migration fixtures. Reset explicitly for a clean demonstration. Real browser interaction/visual QA and other providers have not been validated in this milestone.

The packaged application was restarted after the HTTP booking: the same booking ID remained present, both seed reservations remained intact, and Flyway reported the schema up to date with no migrations reapplied. The packaged frontend and venue endpoint returned HTTP 200.


The third slice implements revisions for unbooked events, stored cost comparison, and stale-proposal/concurrent-booking guards. See [revision-slice.md](revision-slice.md).
