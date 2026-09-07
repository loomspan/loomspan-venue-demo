# The Annex: scope and demonstration design

This document describes the implemented first-version boundary and supersedes the original aspirational brief. The audience is developers evaluating Loomspan. Use the [developer walkthrough](demo-walkthrough.md) to demonstrate it and [implementation status](implementation-plan.md) to track release preparation.

The app turns a brief into confirmed requirements, a validated proposal and a booking. Every feature should make that outcome understandable, establish correctness, or expose a specific Loomspan capability. A complete venue-management product is outside scope.

## What we build, and why

| Area | Implemented minimum | Loomspan value or application responsibility |
| --- | --- | --- |
| Intake | One brief, visible reference date, optional PNG/JPEG agenda; editable human review | Direct YAML execution, attachment input, nullable structured output, separate model alias |
| Event types | Room-only meeting or two-room workshop | Different bounded specialist work for different confirmed requirements |
| Space | Three rooms, theater seating, one fixed event block | Nested space planner with Java capacity/availability checks |
| Catering | Fixed catalog; standard/vegan portions; one attendant; 100-person delivery capacity | Narrow specialist, structured result and deterministic quantities/prices |
| Technical services | Presentation kit; optional livestream kit and qualified operator | Work depends on the selected plenary room and confirmed service flags |
| Proposal | One least-cost validated result per assessment, or no option/failure | Structured outputs and direct-child evidence, followed by Java business validation |
| Revisions | Explicit structured edits before booking; immutable history and cost comparison | Fresh invocations over application-owned revisions |
| Manager credit | One fixed $100 credit on an eligible unbooked proposal | Annotation-defined Java skill through SkillTemplate and Spring role enforcement |
| Acceptance | Atomic booking and all resource reservations, with locked rechecks | Explicit application transaction; acceptance is not an exposed agent capability |
| Developer inspection | Source/quote records and session references, optional Console | Inspect actual plans, calls, usage, retries and overlap without a second trace UI |

The app has five YAML skills: `interpretEventBrief`, `assessEvent`, `planEventSpace`, `assessEventCatering` and `planEventTechnicalServices`. Assessment and space are planners. Intake, catering and technical services use direct execution.

Seven read-only Java skills retrieve/check/quote venue resources; `applyRoomCredit` is the restricted Java write. The authoritative declarations are in [skills](../src/main/resources/skills), [VenueSkills.java](../src/main/java/demo/annex/VenueSkills.java) and [CreditSkills.java](../src/main/java/demo/annex/CreditSkills.java). Booking is an application action, not an extra skill for feature-count purposes.

## Fixed business rules

- One venue, **The Annex**, using USD and `America/Los_Angeles`; no tax calculation.
- Three rooms: Alder 120 seats/$900, Birch 60/$600, Cedar 40/$300. These are flat event-block charges.
- Events run 13:00–18:00. Rooms/equipment/operator reserve 12:30–18:30; catering and its attendant reserve 12:30–13:30. Intervals are half-open.
- A workshop has even attendance, two equal breakout groups, theater seating, a fixed agenda, and no breakout AV. Lunch counts must equal attendance. Livestream requires presentation equipment.
- Two equipment types and two staff members suffice. Catalog reference data is seeded; there are no administration forms.
- Model output is a proposal, not a trusted business instruction. Java checks supported candidates, exact arithmetic, original resource versions and availability. No quote holds inventory.
- Credits require a current, READY, unbooked proposal with room subtotal at least $500. The original quote stays intact; booking uses the payable total. Credits do not stack or carry to a fresh proposal.
- Alex and Morgan are selectable simulated identities mapped to server-defined roles. This exercises real authorization but does not authenticate a real person. Models cannot supply the role, approver or credit amount.
- Requirements can be revised only before booking. Historical proposals cannot be accepted. Acceptance and competing mutations use application transactions/locks.
- A historical agenda does not change the fixed schedule or authorize current attendance/counts. Explicit review remains required. Intake has no business-write tools.

## How much planning is enough

The root sees a narrow specialist surface. Its prompt guides supported meeting/workshop decomposition; the model does not invent a venue workflow from arbitrary capabilities. The space specialist owns its local room checks. Independent space/catering work is eligible for concurrency; room-dependent technical work follows space, and quoting follows the specialists.

Evidence annotations express required direct-child support. They do not prove factual accuracy, arithmetic or the validity of a particular allocation. Those remain deterministic application checks. A parallel group does not prove measured overlap; use actual runtime evidence when presenting concurrency.

A new feature must justify its business scenario, the framework behavior it exposes, and why seeded data or a fixed rule cannot demonstrate it more simply.

## Persistence and interface

React + TypeScript + Vite provides a lightly styled desktop workspace. The packaged Spring Boot JAR serves the compiled UI. Spring Data JPA/Hibernate uses file-backed H2, with Flyway V1–V6 migrations and Hibernate schema validation. Only Java and configured model access are required to run the built JAR; Node/npm are build dependencies.

H2 stores intake sources/interpretations, immutable event revisions, assessments, original quotes/allocations, credits and bookings. Agenda images live in local app-owned storage. Startup retains these records; explicit reset removes default demo data and app-owned images. Execution traces are not the business record.

The UI shows coarse progress, source review, one quote, supporting records, revision comparison and a reservation schedule. Detailed runtime inspection belongs in optional Loomspan Console. The separately hosted `prototype/` is an ignored simulated design reference, not a runtime or distributable dependency.

## Deliberately excluded from this version

- Multiple proposal cards, enhanced-lunch preferences or general optimization. Enhanced lunch exists in the catalog, but the current policy selects the least-cost valid package.
- Conversational revisions, arbitrary times/layouts, multi-day events or custom agenda scheduling.
- PDF/Word intake, OCR infrastructure, document management, generated agendas/run sheets, or remote attachment retrieval.
- Booked-event amendments, cancellation, rescheduling, invoices, payments, contracts or e-signatures.
- Real login, user administration, multiple venues/tenants or production deployment.
- Email, outbound messages, CRM, supplier integrations, public booking, floor-plan editing, staff administration or payroll.
- A fault-injection UI or one-click live fixture branches. Failure and race behavior is exercised through application flows and focused tests.

The original “budget versus preference” scenario and multiple-option wireframe are not unfinished requirements for this version. Missing counts, infeasibility, stale acceptance and permissions remain part of the demonstration; see the runbook for supported branch setups.

## Completion boundary

Functional slices are implemented and their test/browser evidence is recorded in the slice documents. This does not establish production readiness or fresh-checkout reproducibility.

Release preparation still requires a fresh-checkout/build rehearsal with the selected framework revision, a complete presenter/Console rehearsal, CI using an explicit framework revision until artifacts are published, and repository distribution checks. See [implementation-plan.md](implementation-plan.md). These are packaging and verification tasks; new venue features are not required.
