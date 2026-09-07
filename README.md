# Loomspan Venue

A self-contained event operations demo for one venue, built to show how Loomspan combines model-driven planning with deterministic application services.

**Status:** real room-only and workshop assessment and booking are implemented. Workshops combine space, catering and technical specialists into a validated proposal with atomic resource reservations. Unbooked events support confirmed requirement revisions and stored proposal comparison. A manager-only fixed room credit demonstrates authorization. Brief and optional agenda-image intake are implemented with explicit human confirmation. The separate hosted wireframe illustrates the broader concept.

## Run the demo

Prerequisites: Java 21+, Node.js 22.13+ (for building the frontend), configured model access, and Loomspan `0.1.0-SNAPSHOT` installed into your local Maven repository. In the framework checkout, run `./mvnw -pl loomspan-spring-boot-starter -am install` (or `mvnw.cmd` on Windows).

From this project on Windows:

```powershell
$env:OPENAI_API_KEY = 'your-key'
$env:ANNEX_MODEL = 'gpt-4.1'
.\mvnw.cmd package
java -jar target/the-annex-0.1.0-SNAPSHOT.jar
```

On macOS/Linux use `export OPENAI_API_KEY=...`, `export ANNEX_MODEL=gpt-4.1`, and `./mvnw package`. The packaged application serves the React UI at [localhost:8080](http://localhost:8080). No separate frontend server or Docker is required to run the JAR. If `java` is not on PATH, invoke it from your JDK's `bin` directory.

Start with the default **60-person workshop** on October 15, 2026: 50 standard lunches, 10 vegan lunches, presentation and plenary livestream, and a $4,000 budget. Confirm and save, assess with Loomspan, then review the **$2,780 Birch + Cedar proposal** and accept it to reserve all seven resources. The total assumes fresh fixture availability. The **Room-only meeting** selection still demonstrates the $300 Cedar option for 20 people and a $500 budget.

Reservations persist in `data/annex.mv.db`. A booking affects later assessments on the same date. Use another date or explicitly reset for a repeat demonstration. V3–V6 upgrade existing databases and retain prior bookings and requirements.

The demo uses a fixed 13:00–18:00 event block, with 12:30–18:30 room/equipment/operator reservations and 12:30–13:30 catering reservations. Workshops require two equal breakout groups and lunch counts matching attendance. One least-cost proposal is returned per assessment. The server binds to loopback. A labeled demo identity selector simulates Alex (coordinator) and Morgan (manager); Spring Security and Loomspan enforce the manager-only credit. This selector is not production login. See [the workshop slice](docs/workshop-slice.md) for contracts and acceptance criteria.

The standard Maven build installs frontend dependencies, builds React, and includes the UI in the Spring JAR. No Maven profile is needed. Node.js and npm must be available when building; running the packaged JAR only requires Java and configured model access.

For development, run `./mvnw spring-boot:run` and, separately, `npm ci` then `npm run dev` in `frontend/`. Open the URL printed by Vite (normally `http://localhost:5173`) to use the UI in this mode; port 8080 serves the backend API. Vite proxies `/api` to port 8080. `ANNEX_MODEL_BASE_URL` selects an OpenAI-compatible endpoint; `ANNEX_MODEL` selects its model. `.env.example` documents variables but is not loaded automatically.

## Start from a brief and agenda

Choose **Use example agenda**, then **Interpret with Loomspan**. The default brief requests 60 attendees next Thursday, resolved against the visible reference date of October 8, 2026. The historical image mentions 40 attendees; those counts must not become current requirements. Review the extracted date of October 15, fill the missing lunch counts with 50 standard / 10 vegan, and confirm before saving and assessing.

One optional PNG/JPEG image is supported, up to 2 MB. Source briefs and images go to the configured model provider. `ANNEX_INTAKE_MODEL` independently selects the intake model (default `gpt-4.1`); it must support image input. Saved drafts and source images survive refresh and restart in H2 and `data/attachments`. See [the intake slice](docs/intake-slice.md) for scope and failure behavior.

## Revise an unbooked workshop

Before accepting the 60-person proposal, choose **Revise requirements**, change attendance to 90, lunch counts to 75 standard / 15 vegan, and clear livestream. Confirm and save, then assess again. The comparison shows **$2,780 → $3,320 (+$540)** and the change from Birch + Cedar to Alder + Birch. Historical proposals cannot be accepted. Booked events cannot be revised. See [the revision slice](docs/revision-slice.md) for scope and concurrency rules.

## Try manager authorization

On a current unbooked proposal, try **Apply $100 room credit** as Alex to see denial. Switch the **Demo identity** selector to Morgan and repeat to approve one fixed credit when the room subtotal is at least $500. The $3,320 workshop becomes **$3,220 payable**, while preserving the original quote. See [the manager-credit slice](docs/manager-credit-slice.md). Identity selection is a local login simulation, not real user authentication.

## Tests and reset

```powershell
.\mvnw.cmd test
$env:ANNEX_LIVE_TEST = 'true'
.\mvnw.cmd "-Dtest=LiveAssessmentTest,LiveWorkshopTest,LiveRevisionTest,LiveIntakeTest" test
Remove-Item Env:ANNEX_LIVE_TEST
```

Ordinary tests use isolated H2 databases and no model calls. The opt-in live tests use your configured provider and verify the $300 meeting and $2,780 workshop, nested YAML execution, actual space/catering overlap, and booking. Live intake tests check image interpretation, unknown meal counts and brief-only input. Backend tests cover reservation races, stale prices, idempotency, input validation, invalid model output and infeasibility. The frontend build checks TypeScript.

To reset the default demo database, stop the application and run `./scripts/reset-demo.ps1 -ConfirmReset` in PowerShell. On other systems, stop it and remove the local `data/annex.mv.db` file and app-owned UUID-named PNG/JPEG files in `data/attachments`. The PowerShell script removes those default files while preserving other files and custom storage locations. Reset removes intake drafts, saved requests and bookings; Flyway recreates the fixtures at startup. It is never performed automatically on ordinary restarts.

Optional Console integration uses `ANNEX_OBSERVABILITY_ENABLED=true` and a separate `ANNEX_OBSERVABILITY_API_KEY` of at least 32 characters. Completed assessments retain a Loomspan session ID. Normal business records remain independent of Console.

## The application

An employee turns an event request into a feasible proposal using the venue's own rooms, equipment, staff, catering menu, prices, and bookings. The employee can review alternatives, change requirements, compare proposal revisions, and accept an option to create a booking with resource reservations.

The fictional venue is **The Annex**. The demonstration is intended for developers evaluating Loomspan, starting with a clear, lightly styled desktop browser prototype.

> Prepare a proposal for a 60-person customer workshop next Thursday, 1–6 p.m. We need a presentation, two breakout groups, lunch with vegan options, and a livestream. Keep it under $4,000. Here's last year's agenda.

The advisor interprets the brief, selects relevant specialists, checks local records, and produces structured options with exact costs, supporting records, and open questions. Application code validates capacities, availability, prices, permissions, and reservations.

Then change the request:

> Attendance is now 90, and we no longer need the livestream.

The next assessment uses the updated facts. Viewers compare proposals and inspect the actual skill execution in Loomspan Console. The demonstration should show different work as well as different wording.

## Why this showcases Loomspan

- **Selective planning:** a room-only meeting, catered workshop, and livestream event need different capabilities.
- **Useful hierarchy:** specialists own narrow contracts and local child capabilities.
- **Java and YAML together:** models interpret requirements and evaluate alternatives; Java checks rules, calculates prices, and performs controlled writes.
- **Concurrency:** independent investigations can overlap; dependent work waits for its inputs.
- **Contracts and evidence:** structured results populate the UI, with supportability requirements on appropriate claims and deterministic validation of business correctness.
- **Attachments and model selection:** a supplied agenda adds intake context, using a compatible model when needed.
- **Authorization and observability:** restricted adjustments, bounded execution, validation failures, and nested traces are inspectable.

The current app demonstrates planning, specialist hierarchy, Java/YAML composition, concurrency, structured results and transactional booking. Agenda-image intake demonstrates attachment handling and confirmation; the manager-credit branch demonstrates role-based authorization. Framework details must match the selected Loomspan dependency version.

## Deliberately small

One venue, three rooms, a small equipment inventory, fixed catering packages, a handful of staff, and a short seeded booking calendar. The core UI is an event workspace with proposal options, a resource schedule, and revision comparison.

This is not a production venue management platform. There is no public booking website, payment processing, supplier integration, outbound messaging, CRM, floor-plan editor, or workforce management system. Reference data is seeded; administration screens for every table are outside scope.

Read [the scope and demonstration design](docs/scope-and-demo-design.md) for the product boundary, feature rationale, scenarios, architecture, acceptance criteria, and implementation sequence. That document is the implementation brief; this README is the introduction.

The [demo walkthrough](docs/demo-walkthrough.md) makes the workshop concrete with venue fixtures, checked quotes, screen states, revision changes, and booking outcomes. It is the content brief for wireframes and implementation stories.

The clickable wireframe lives in [prototype](prototype/README.md). It covers confirmed intake, simulated assessment, proposal selection, the 90-person revision, comparison, booking, and a stale-availability example. Optional Loomspan notes explain intended framework responsibilities.

Three selectable branches also demonstrate a room-only meeting, an unavailable livestream kit followed by an explicitly revised request, and coordinator denial versus manager authorization of a fixed room credit. Each branch uses isolated simulated state.

## Intended setup

- Java 21 / Spring Boot backend with the Loomspan starter and deterministic application services.
- React + TypeScript + Vite frontend calling Spring REST endpoints.
- Spring Data JPA / Hibernate with file-backed H2 for business records.
- Flyway SQL migrations for schema creation and initial demo data; Hibernate validates the schema.
- Local attachment storage and resettable seeded business data.
- Configured model access; Loomspan Console is optional developer tooling.

The core demo needs no external business services beyond configured model access.

The completed demo will live in its own GitHub repository, independent of the framework. During development it will consume `ai.loomspan:loomspan-spring-boot-starter:0.1.0-SNAPSHOT` installed into the local Maven repository from the framework checkout. See [architecture decisions](docs/architecture-decisions.md) for the repository boundary and setup plan.

## Related framework

[Loomspan Framework](https://github.com/loomspan/loomspan-framework), with the sibling checkout at `../loomspan-framework` used as the current design reference.
