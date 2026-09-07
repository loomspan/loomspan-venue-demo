# Loomspan Venue: scope and demonstration design

## Status and purpose

This document defines the proposed first version. It describes intended behavior, not implemented functionality. It replaces the repair-shop concept as the product direction.

The [demo walkthrough](demo-walkthrough.md) supplies concrete fixtures and expected outcomes for the central workshop, and is the next input to wireframes and implementation stories.

Build a small, believable application in which a venue employee converts an event brief into a proposal and a committed booking. Use that workflow to demonstrate Loomspan's dynamic decomposition, specialist boundaries, Java/YAML composition, contracts, authorization, and observable execution.

The audience is developers evaluating or learning Loomspan. They should understand the business outcome before examining the framework trace, and be able to run and modify the demo themselves.

The goal is enough business behavior to make agent decisions consequential. Completeness as a venue product is not a goal. Every addition must make a core scenario understandable, establish correctness for a demonstrated action, or expose a specific framework capability.

## Product boundary

The business operates one fictional venue with its own rooms, equipment, staff, and catering packages. Availability and pricing live in this application. No external supplier needs to answer for a proposal to be assessed.

Initial event types are meetings, workshops, and presentations. Weddings, festivals, multi-day conferences, ticketed events, and off-site services are outside scope.

Two seeded identities demonstrate coordinator and manager permissions. There is no customer portal, signup, identity administration, or production identity-provider integration. Demo identities must still establish trusted server-side authentication; a role supplied in a model prompt cannot grant access.

| Area | Build | Do not build |
| --- | --- | --- |
| Intake | Brief, dates/times, attendance, budget, priorities, optional agenda attachment | Lead pipeline, customer accounts, email ingestion, general document management |
| Space | Three rooms, supported layouts, capacity, setup/teardown buffers, reservations | Floor-plan editor, seating optimization, building-code certification |
| Catering | Fixed packages, per-person prices, declared dietary compatibility, capacity limits | Recipes, kitchen inventory, purchasing, nutritional or allergen safety guarantees |
| Equipment | Small catalog of countable resources and explicit compatibility rules | Maintenance, serial-number tracking, supplier rentals, delivery logistics |
| Staffing | A few staff with qualifications and available intervals; assignments to required duties | Payroll, leave, employment rules, shift bidding, general workforce scheduling |
| Proposals | At most three validated options, exact costs, supporting records, questions, infeasibility reasons | Contracts, e-signatures, arbitrary templates, exhaustive optimization |
| Booking | Accept an option and reserve resources atomically | Payments, deposits, refunds, tax accounting, invoices, cancellation fees |
| Changes | Assess revised requirements and compare proposals before acceptance | Committing amendments to bookings or managing the ongoing event lifecycle |
| Permissions | Coordinator/manager actions and one fixed manager adjustment | Custom role editor, organization hierarchy, tenant administration |
| Operations | Seed/reset commands, small reference views, understandable failures | Enterprise deployment, high availability, reporting warehouse, production monitoring suite |

Prices use one configured currency and a documented demo pricing policy with no tax calculation. Events use the venue's timezone. The UI shows explicit dates; relative phrases such as “next Thursday” resolve against a visible reference date, never an unexplained moving clock.

## Core user journey

1. Open a seeded event request or enter a brief through the same intake flow.
2. Confirm structured constraints and inspect attachment-derived details.
3. Run an assessment. The advisor chooses relevant investigations using explicit inputs and current venue records.
4. Review up to three options, or a useful result explaining missing information or infeasibility.
5. Change a constraint and run a fresh assessment. Compare revisions and supporting records.
6. Accept a valid option. The server rechecks permissions, record versions, prices, availability, and proposal validity, then creates the booking and reservations in one transaction.
7. See the booking on the resource schedule. A stale or conflicting option produces a clear reassessment action.

Each assessment is a fresh Loomspan invocation. The application owns durable revisions. This is not a demonstration of missions paused for days, autonomous background monitoring, or automatic event execution.

The first version demonstrates changes before acceptance. Assessing and committing changes to an existing booking would introduce a separate amendment and reservation-replacement workflow and is deferred.

## Minimum interface

The venue is named **The Annex**. The prototype is a clear, lightly styled desktop browser experience for **developers evaluating Loomspan**. Use realistic venue operations to explain outcomes, with a secondary, optional explanation of the framework capabilities demonstrated. These explanations describe intended behavior; they must not masquerade as live execution traces.

Use the walkthrough's real fixture content in the clickable prototype. Prototype state is disposable and simulated; no model connection or backend is required to evaluate the flow. Clearly label simulated assessment and booking behavior. The prototype does not select the production frontend stack.

Build one event workspace and a small resource schedule view.

- **Request panel:** brief, explicit constraints, attachment, and relevant event facts.
- **Proposal area:** option cards with agenda segments, room assignments, resources, price breakdown, supporting records, and open questions.
- **Action area:** assessment state, revision comparison, accept action, restricted adjustment, and booking result.
- **Resource schedule:** a simple room/equipment/staff reservation view that explains feasibility and shows committed changes. No drag-and-drop scheduling is required.

Reference catalogs can be read-only tables or contextual drawers. Seed files provide data editing for developers. Do not build CRUD screens simply because an entity exists in the database.

Show coarse assessment progress, completion, and actionable errors. Detailed plans, provider attempts, tokens, and skill internals belong in Loomspan Console. Do not build a second trace viewer into the product.

## Feature-to-demonstration mapping

| Loomspan capability | Business reason | Minimum demonstration and boundary |
| --- | --- | --- |
| Dynamic HTN planning | Requests need different services | A room-only request omits catering and technical investigations; a workshop selects relevant specialists. No hardcoded scenario-to-plan mappings. |
| Nested specialists | Space and technical fulfillment have narrower decisions than the entire event | Root planner plus at least one justified specialist planner. Other specialists may use direct execution. |
| Local child visibility | Specialists need their own tools and contracts | Narrow direct child surfaces. Parents consume specialist results without depending on internal leaves. |
| Java/YAML composition | Interpretation and exact business rules require different mechanisms | Models propose arrangements; Java validates timing/resources, eligibility, and arithmetic. |
| Concurrent tasks | Independent service investigations can overlap | Demonstrate actual overlap in an eligible run. Room-dependent equipment work follows the room inputs it needs. |
| Structured contracts | Results drive ordinary UI components | Explicit input/output contracts, validated structured results, and application validation. |
| Evidence contracts | Cost and availability claims need supporting capabilities | Appropriate direct-child supportability requirements. Successful calls do not prove factual truth, arithmetic, or per-option validity. |
| Attachments | Last year's agenda adds session and technical requirements | One bundled agenda image and a compatible model path. No general OCR platform or broad file-type support. |
| Per-skill model selection | Intake and complex planning can have different needs | Configurable aliases and one tested default configuration. Multiple providers and a benchmarking UI are unnecessary. |
| Authorization | Coordinators cannot grant a manager adjustment | One fixed deterministic adjustment available to managers, enforced in application actions and any exposed skill. |
| Limits and retries | Difficult or malformed requests must terminate understandably | Bounded execution and a small opt-in technical test harness. Never weaken validation to make a demo succeed. |
| Observability | Developers need to inspect the work performed | Optional Console connection showing nested execution, child results, actual overlap, failures, and usage. |

Reuse deterministic services between UI actions and Java skills. Do not duplicate business rules in prompts or build a general workflow engine around Loomspan.

The first version has one assessment entry skill. Additional roots reusing specialists are a possible follow-up demonstration, not an initial requirement.

Intake interpretation is a separate direct YAML invocation before employee confirmation. The assessment entry receives confirmed structured requirements; it does not need to repeat attachment interpretation for each revision.

## Proposed skill shape

Names and counts are provisional. Expect roughly 10–15 capabilities; this is a sizing guide, not a quota.

| Responsibility | Likely implementation |
| --- | --- |
| Assess an event | Root YAML planner selecting investigations and assembling candidate options |
| Interpret brief/agenda | Focused YAML skill producing requirements and uncertainties |
| Plan space | YAML specialist, with planning if room/session selection warrants it |
| Assess catering | Focused YAML specialist using package and capacity checks |
| Plan technical services | YAML specialist selecting equipment and qualified staff |
| Retrieve venue facts | A few cohesive Java skills for rooms, catalogs, and availability |
| Validate a candidate | Java skill checking explicit session/resource allocations and returning violations |
| Calculate a quote | Java skill calculating an exact breakdown from catalog selections |
| Apply manager adjustment | Restricted Java capability using one fixed policy |

Final synthesis can remain in the root. Booking acceptance is an explicit application action; it does not need to be an agent skill to increase feature coverage.

Pass identifiers, intervals, quantities, and catalog choices through explicit contracts. Trusted caller identity comes from the application, not model-selected arguments.

The model selects and explains a bounded set of candidates. It is not expected to solve arbitrary scheduling or prove that no arrangement exists. Infeasibility language must be scoped to supported configurations and checked candidates.

## Business correctness required for the demo

- Capacity is defined per supported room layout. Every agenda segment must fit its assigned room.
- Reservations include setup/teardown time. Use half-open intervals consistently so adjacent reservations have unambiguous behavior.
- Shared equipment allocations cannot exceed stock during overlapping intervals.
- Staff assignments require the declared qualification and available, non-overlapping time.
- Catering choices satisfy modeled package constraints. Unsupported dietary requests produce a question or manual-review requirement.
- Quotes use catalog rates and explicit quantities. Manager adjustments follow the fixed policy and record the actor.
- Model-produced options remain untrusted until validated. Missing required decisions cannot silently become bookable defaults.
- Acceptance revalidates under transaction protection. Concurrent requests cannot both reserve an exclusive resource or exceed pooled stock.

Keep scheduling to discrete agenda segments and supported arrangements at one venue. Use deterministic conflict checks and seeded choices. Do not add a general optimization solver unless a core scenario establishes its necessity.

## Data and persistence

Seed three rooms, a handful of layouts, approximately six equipment types, three catering packages, four staff members, and a short calendar of reservations. Design exact fixture values together so scenario outcomes are feasible and verified.

These counts are estimates, not minimums. The walkthrough starts with one layout per room, two equipment types, and two staff; add records only when another scenario requires them.

Persist only what supports the workflow:

- Event requests and explicit requirement revisions.
- Local attachment metadata and files.
- Reference catalogs and availability records.
- Assessment revisions: input snapshot, relevant record versions, validated options, questions, status, and optional execution/session reference.
- Bookings, resource allocations, and adjustment audit records.

Console traces are not the business record. Bookings must remain understandable without model execution history.

Provide an explicit reset command for demo data and attachments. Use a visible demo calendar anchor so fixtures remain understandable as the real date changes. Startup must not silently erase user changes.

## Six core scenarios

| Scenario | Request or changed fact | Observable result |
| --- | --- | --- |
| 1. Simple meeting | Small attendance, room only | Feasible priced proposal with a short plan; no unrelated catering or livestream work |
| 2. Full workshop | Presentation, breakouts, catering, livestream, agenda attachment | Relevant specialists, useful nesting, validated allocations, exact quote, and eligible concurrent investigations |
| 3. Attendance/service change | Workshop grows from 60 to 90; livestream removed | Revised space/catering requirements and removal of livestream resources; compare execution as well as output |
| 4. Resource conflict | Needed room or equipment is unavailable | Supported alternative with tradeoffs, or scoped infeasibility; no invented inventory |
| 5. Budget versus preference | Higher budget is allowed to retain a preferred arrangement | Different feasible recommendation explained by the changed priority; Java still calculates costs |
| 6. Restricted adjustment | Same qualifying adjustment attempted by coordinator and manager | Coordinator denied; manager succeeds through the fixed rule and audit path |

Scenario controls change real request fields or venue records. Names are fixture selectors and UI conveniences, not instructions to return predetermined recommendations.

Also verify incomplete requests, stale proposal acceptance, and competing booking attempts. These need focused correctness checks, not additional polished workflows.

Malformed output, provider failure, and execution limits belong in controlled integration tests or an explicitly labeled developer harness. Do not depend on a live model failing on cue or inject faults into ordinary business data.

## Architecture and implementation sequence

Use Java 21 / Spring Boot, React + TypeScript + Vite, Spring Data JPA / Hibernate, file-backed H2, Flyway SQL migrations, and local attachment storage. The demo is a standalone project intended for its own GitHub repository. See [architecture decisions](architecture-decisions.md).

Integrate through the supported `ai.loomspan.api` surface. Do not depend on internal framework classes or unsupported bean replacement. Initially consume `0.1.0-SNAPSHOT` from the local Maven repository; record the tested framework commit and model configuration before claiming compatibility.

1. **Fixtures and rules:** venue data, expected scenario outcomes, interval checks, pricing, and transactional reservations. Establish supported arrangements before writing prompts.
2. **Complete vertical slice:** assess a simple event, display a validated proposal, accept it, and show the booking. Keep the UI narrow.
3. **Meaningful decomposition:** full workshop, justified specialists, selective branches, and actual independent concurrency.
4. **Comparison and authorization:** requirement revisions, six scenarios, stale acceptance handling, and the manager adjustment.
5. **Portable demonstration:** agenda intake, optional Console setup, safeguards, failure tests, seed/reset instructions, and a short demo script.

Do not build catalog administration before the first assessment-to-booking slice works.

## Definition of done

- A new developer can follow documented setup with local business data and configured model access.
- All six scenarios use the same application services and skill definitions.
- Simple and complex requests show meaningfully different capability use. The complex case shows a useful nested mission and observed overlap with the tested configuration.
- Structured options expose supporting records, exact costs, and unresolved requirements. Only validated complete options are bookable.
- Changed requirements create comparable durable revisions instead of overwriting assessments.
- Acceptance creates visible reservations and rejects stale/conflicting requests without partial booking writes.
- The restricted adjustment is enforced for both seeded identities, including skill execution when exposed.
- Focused tests cover calculations, allocation validity, authorization, transactional conflicts, contracts, and bounded failures.
- A live smoke run verifies planning and attachment behavior using the documented configuration. Protocol tests alone do not establish live planning quality.
- Console remains optional; the application explains business outcomes without framework expertise.

These criteria do not imply production readiness.

## Scope control and open implementation choices

Before adding a feature, identify the scenario it enables, the Loomspan behavior it makes visible, and the smallest implementation that demonstrates it. If seeded data, a read-only view, or one fixed rule suffices, use that.

Defer features justified primarily by “a real venue system would have this.” Preserve the exclusions on public booking, payments, communications, multi-venue support, comprehensive administration, and booking amendments.

The frontend and persistence stack are selected in the architecture decisions. Remaining fixture details, API contracts, and tested model aliases are implementation choices. Preserve the walkthrough's fixed adjustment policy and this scope. Expand the product boundary only through an explicit revision of this document.

## Framework grounding

This design uses the sibling Loomspan checkout's `README.md`, `agent-skills/loomspan-docs/references/skill-authoring/mental-model.md`, and `checklists/evaluate-a-skill-design.md`. They explain direct execution versus explicit planning, local visibility, deterministic Java responsibilities, and the cost of unnecessary planning boundaries.

Consult matching dependency documentation before implementing contracts, concurrency, authorization, attachments, or limits. This document specifies demonstration intent; it does not extend supported framework behavior.
