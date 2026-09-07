# Working slice 02: confirmed workshop

Build one complete, least-cost workshop recommendation and atomic booking. Keep the room-only flow. Intake remains a structured confirmation form; attachment interpretation, alternative proposal cards, revisions, manager adjustments, authentication and arbitrary schedules remain later work.

## Acceptance scenario

Northstar customer workshop, October 15, 2026: 60 attendees, two equal discussion groups, 50 standard and 10 vegan lunches, presentation and plenary-only livestream, $4,000 hard cap. Theater seating and the fixed 13:00–18:00 agenda are explicitly confirmed. Reserve rooms and technical resources for [12:30,18:30), catering capacity and Sam for [12:30,13:30), in America/Los_Angeles.

Birch is the plenary and one breakout room; Cedar is the other breakout room. Quote: rooms $900 + boxed lunch $1,080 + presentation $200 + stream $300 + Lee's six hours $300 = **$2,780**. Sam is included. No reservation exists before acceptance.

## Contracts and Loomspan responsibilities

- Immutable requests add `eventType` (MEETING or WORKSHOP), standard/vegan meal counts, presentation and livestream flags. Workshops have exactly two equal breakout groups and lunch for everyone. Odd attendance, inconsistent meal counts, and livestream without presentation are rejected before model work.
- `assessEvent` receives eventId and confirmed eventType from the application. Space is required; catering and technical specialists are selected only for workshop requirements. The root retains the meeting result contract and adds nullable breakoutRoomId and lunchPackageId.
- `planEventSpace(eventId,eventType)` uses room checks for meetings and a bounded set of checked two-room candidates for workshops. The root passes the confirmed type before the nested planner generates its task list; learning the type from a subsequent tool call is too late to choose task bindings. It chooses a least-cost valid assignment.
- `assessEventCatering` is a direct YAML specialist backed by Java catalog, dietary-count, delivery-capacity and attendant checks. It returns a supported lunch package or null.
- Space and catering are independent read-only work, eligible for one explicit parallel group. `planEventTechnicalServices` follows space and checks the selected plenary assignment, equipment stock and staff time. It is a direct YAML specialist with Java checks.
- `validateEventQuote` combines the exact selected room and package IDs with persisted requirements. It returns deterministic quote lines, allocations, versions and violations. Final output is checked again by application code; model output never controls prices, counts, rates, time intervals or reservation writes.

One recommended option keeps this slice small. Enhanced lunch remains a real catalog alternative, but the lower-cost preference selects boxed lunch when both are feasible. Unsupported requirements require a future intake/manual-review flow rather than silently being accepted.

## Persistence and booking

V3 adds service inventory, immutable workshop fields, versioned proposal allocation snapshots and a common reservation table. It backfills existing room bookings without resetting data or changing V1/V2. A booking remains one aggregate with multiple resource reservations.

Acceptance locks event, assessment, sorted rooms, then sorted service resources; every booking path follows this order. It rechecks all intervals, capacities, inventory, versions and quote lines before saving any booking. A conflict returns HTTP 409 without partial reservations. Duplicate acceptance returns the original booking. Assessment has no long-running database transaction around the model call.

## Verification

Check the $2,780 scenario; room-only regression; complete persisted allocation and quote lines; dietary counts; delivery limit; half-open boundaries; unavailable equipment/staff; stale price/version; false model infeasibility and tampered selections/totals; competing multi-resource bookings; rollback and idempotency. Use isolated H2 databases. An opt-in live test exercises both YAML modes and public execution observations; observed branch timestamps, rather than configured concurrency alone, establish actual overlap. Package the frontend and inspect the real UI.

## Validation record

Validated with Java 21, Spring Boot 4.1.0, the locally installed Loomspan `0.1.0-SNAPSHOT` from framework commit `d202b204ea41a9cfee0364221888df157b469bc3`, and the configured `gpt-4.1` model. The standard Maven package build passed with 24 tests: 22 ordinary tests and two opt-in live tests. The frontend TypeScript and production build passed and its assets are included in the JAR.

The live workshop test returned Birch + Cedar, FOOD-BOX, and $2,780, with seven reservations after acceptance. Its public execution events showed space running 06:40:21.874–06:40:26.250 UTC and catering running 06:40:21.833–06:40:23.203 UTC on September 7, 2026: actual overlap, followed by technical checks and quote validation. The live meeting regression returned Cedar at $300.

Packaged browser verification used a separate database under `target/` and port 8081. Confirming enabled Save; changing meal counts cleared confirmation; mismatched counts blocked Save. The corrected real browser flow saved the workshop, assessed it at $2,780, displayed quote lines and supporting records, and accepted booking `bb25f5e2-dabb-45d2-a4fc-244a1bcd410a` with all seven resource reservations. Desktop layout was visually checked. These checks did not write to the user's ordinary `data/annex.mv.db`.

Earlier browser assessments exposed a task-selection error: the nested space planner learned the workshop type only after it had generated a single-room task list. Application validation rejected the false no-option result without saving a proposal or reservations. The root now passes eventType explicitly before nested planning, and the final live and browser runs passed. Completed but application-rejected assessments now retain the diagnostic session ID. Model execution can still fail; no simulated fallback or weakened validation was introduced.

After restarting the packaged application against the verification database, the same booking ID, total and seven reservations remained present. Flyway reported the schema up to date without reapplying migrations. The temporary verification server was then stopped.
