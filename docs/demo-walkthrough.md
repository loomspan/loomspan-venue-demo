# Demo walkthrough: a workshop that changes

## Purpose and status

This is the concrete content brief for wireframes, seed data, and implementation stories. It specifies the broader intended behavior. The room-only meeting now has a real application implementation; workshop services and the generated agenda asset remain future work. Prices and venue rules below are fictional demo fixtures, not market estimates.

Read this with [scope and demonstration design](scope-and-demo-design.md). The main demonstration is one event request, two assessments, and one booking. Side branches exercise failure and permission states without expanding the product lifecycle.

The viewer should see the advisor select relevant work, produce checkable options, respond to changed requirements, and hand a validated option to deterministic booking code.

## Starting state

- Venue: **The Annex**, a single fictional conference venue.
- Employee: **Alex**, coordinator. **Morgan**, manager, is available for the permission branch.
- Currency: USD. No taxes, deposits, or payment processing.
- Visible demo reference date: Monday, October 12, 2026.
- Event date: Thursday, October 15, 2026, in `America/Los_Angeles`.
- Event: **Northstar customer workshop**, request `EVT-001`.
- Start from an explicitly reset seed dataset. No assessment or reservation exists for this request.

The displayed date is an application demo setting. It is not a change to the operating system clock. The UI confirms the exact date extracted from “next Thursday.”

## Fixture data that makes the story work

### Rooms

| ID | Room | Supported theater capacity | Flat event-block price |
| --- | --- | --- | ---: |
| ROOM-A | Alder Hall | 120 | $900 |
| ROOM-B | Birch Room | 60 | $600 |
| ROOM-C | Cedar Room | 40 | $300 |

This walkthrough uses one supported theater layout per room. Breakouts are seated discussion groups; they do not require tables or furniture changes. Room rates cover the 1–6 p.m. block, including a 30-minute buffer before and after. There is no hourly room-rate calculation or prorating in this fixture.

All rooms assigned to an option are reserved for `[12:30, 18:30)`, even when a breakout room is used for only part of the agenda. This deliberately avoids room turnover and segment-based rental pricing. Agenda segments still determine capacity requirements.

Existing seed reservations, already including their buffers:

- Cedar: `[09:00, 12:30)` for another meeting.
- Alder: `[18:30, 21:30)` for another event.
- Birch: no conflicting reservation.

Half-open intervals mean these existing bookings do not conflict with the workshop block. No rooms may be invented or borrowed from another venue.

### Catering

| ID | Package | Price/person | Fixture compatibility |
| --- | --- | ---: | --- |
| FOOD-BOX | Boxed lunch | $18 | Standard and vegan portions |
| FOOD-PLUS | Enhanced boxed lunch | $24 | Standard and vegan portions; extra side and dessert |
| FOOD-DRINK | Drinks service | $6 | Does not satisfy a lunch requirement |

Lunch delivery capacity is 100 people for the event's 1 p.m. service. Standard and vegan portions cost the same within a package. Counts must sum to attendance. A lunch package includes delivery/setup by the catering attendant for `[12:30, 13:30)`; reserve that staff time with no separate charge. Capacity and staff availability are checked from records.

No allergy is asserted in the core request. An allergy or unsupported dietary requirement is a manual-review question; package metadata is not an assurance of food safety.

### Technical resources and staff

| ID | Resource | Stock | Flat price/unit |
| --- | --- | ---: | ---: |
| EQ-PRESENT | Presentation kit: display/projector and audio | 2 | $200 |
| EQ-STREAM | Livestream kit: camera and encoder | 1 | $300 |

Both kits support all three rooms. These two types are sufficient for this walkthrough; do not add catalog types solely to meet an estimated entity count. A livestream kit requires a presentation kit and a qualified streaming operator. Presentation-only operation is self-service in this fixture.

Allocate kits to the plenary room for `[12:30, 18:30)`. The stream covers plenary sessions only; no equipment is required in breakout rooms. These are confirmed requirements, not silent advisor assumptions.

| Staff ID | Name | Qualification | Available | Rate |
| --- | --- | --- | --- | --- |
| STAFF-LEE | Lee | Streaming operator | 12:30–18:30 | $50/hour |
| STAFF-SAM | Sam | Catering attendant | 12:30–13:30 | Included in lunch package |

A streaming assignment covers the entire six-hour reservation block, including setup, breaks, and teardown: $300. Sam can fulfill one lunch delivery in this interval, up to the 100-person capacity. Neither staff member has a conflicting core-fixture assignment.

The fixture needs only these two staff. Additional staff are justified only by another scenario, not by the earlier approximate seed count.

## Act 1: enter and confirm the request

Alex opens the workshop and enters:

> Prepare a proposal for a 60-person customer workshop next Thursday, 1–6 p.m. We need a presentation, two breakout groups, lunch with vegan options, and a livestream. Keep it under $4,000. Here's last year's agenda.

The bundled agenda image, to be created later, contains:

| Time | Agenda item |
| --- | --- |
| 13:00–13:30 | Boxed lunch and welcome |
| 13:30–15:00 | Presentation; stream this session |
| 15:00–15:15 | Break |
| 15:15–16:15 | Two simultaneous, equal-sized discussion groups; no AV needed |
| 16:15–16:30 | Break |
| 16:30–18:00 | Plenary Q&A; stream this session |

The attachment establishes a suggested agenda, not authoritative current attendance or dietary counts. `interpretEventBrief` is a direct YAML intake skill invoked before assessment. It extracts requirements and uncertainties; the employee confirms the structured result.

Alex confirms:

- October 15, 2026, 13:00–18:00; all times local to the venue.
- 60 attendees; two equal breakout groups of 30.
- 50 standard lunches and 10 vegan lunches; either lunch package is acceptable.
- Theater seating is acceptable throughout; attendees may eat boxed lunches in their seats.
- Livestream only the plenary sessions; breakout rooms need no technical equipment.
- $4,000 is a hard cap; among compliant options, prefer lower cost.

**Screen result:** a confirmed requirement summary, attachment preview, and enabled **Assess event** action. Extracted facts remain editable. Missing meal counts or an unresolved date keep the request in **Needs clarification**, with the relevant question visible. No booking action is available.

The original brief intentionally needs a small confirmation step. The core scripted run supplies these answers; it must not silently infer them from last year's event.

## Act 2: assess 60 attendees

Alex selects **Assess event**. The product displays coarse progress and then structured options. It does not display invented live task labels or a second Console timeline.

Expected responsibilities, rather than a prescribed exact trace:

1. `assessEvent` receives the confirmed requirement revision.
2. `planEventSpace` investigates a 60-person plenary and two simultaneous 30-person breakouts using room capacities and availability.
3. `assessEventCatering` checks supported lunch packages, counts, delivery capacity, and the attendant. This can overlap the independent space investigation.
4. `planEventTechnicalServices` uses candidate plenary assignments to check kits and streaming staff. Checks dependent on space results wait for those results.
5. Java validates complete candidate allocations and calculates quotes for the exact candidate selections.
6. The root synthesizes validated options and a recommendation in structured output.

`assessEvent` and `planEventSpace` use planning mode. Catering and technical services initially use direct execution with Java children. Intake is a separate invocation and is not repeated during assessment. Candidate validation and quoting are Java capabilities; synthesis stays in the root.

The runtime plan may vary. Verify relevant capabilities, dependencies, and supporting results, not exact prose, task order among independent work, or an exact number of model calls. Actual overlap must be observed in Console before claiming that a run demonstrated concurrency.

### Expected option A: practical workshop

- Birch hosts all 60 attendees for lunch and plenaries.
- Breakouts use Birch for 30 and Cedar for 30. The rooms keep their theater layouts.
- Birch and Cedar are reserved 12:30–18:30.
- One presentation kit and one livestream kit are assigned to Birch for the full block.
- Lee is reserved 12:30–18:30; Sam is reserved 12:30–13:30.
- Boxed lunches: 50 standard, 10 vegan.

| Quote line | Calculation | Amount |
| --- | --- | ---: |
| Birch | Flat block | $600 |
| Cedar | Flat block | $300 |
| Boxed lunch | 60 × $18 | $1,080 |
| Presentation kit | 1 × $200 | $200 |
| Livestream kit | 1 × $300 | $300 |
| Streaming operator | 6 × $50 | $300 |
| **Total** | | **$2,780** |

The recommendation explains the $1,220 budget headroom and that Birch's plenary capacity is fully used. Capacity of 60 is allowed; the advisor must not invent a mandatory spare-capacity rule.

### Expected option B: enhanced lunch

The same allocations with enhanced boxed lunches cost **$3,140**: $900 rooms + $1,440 catering + $500 equipment + $300 operator. This is $360 more, leaving $860 below the cap.

Two options are sufficient. Do not fabricate a third to fill a layout. These are reference feasible options, not hardcoded model responses or a claim that other valid combinations cannot exist. Deterministic checks must reproduce their totals; live evaluation accepts other valid options but checks that the recommendation respects the stated lower-cost preference.

**Screen result:** option cards with the agenda, allocations, quote lines, budget headroom, and record links. Room claims link to room/availability records; catering and equipment claims link to their catalog and availability records. Each quote is bound to its candidate ID and input/catalog versions so results from different options cannot be accidentally combined.

Assessment revision 1 persists the confirmed inputs, outcomes, supporting record versions, and optional execution reference. No resource is reserved yet. Evidence contracts support appropriate direct-child claims; Java still verifies each candidate's concrete values.

## Act 3: change attendance and remove livestream

Before accepting an option, Alex enters:

> Attendance is now 90, and we no longer need the livestream. Keep everything else the same.

The employee confirms two groups of 45 and lunch counts of 75 standard and 15 vegan. The previous meal counts do not automatically scale without confirmation. The application creates requirement revision 2 and marks revision 1's options as historical and unavailable for acceptance against the changed request.

Run a fresh assessment. The updated inputs reuse the confirmed agenda; the attachment does not need to be reinterpreted.

### Expected revised practical option

- Alder hosts all 90 attendees for lunch and plenaries: Birch's capacity of 60 no longer suffices.
- Breakouts use Alder for 45 and Birch for 45: Cedar's capacity of 40 is too small.
- Alder and Birch are reserved 12:30–18:30. Alder's evening reservation begins exactly when this block ends.
- One presentation kit is assigned to Alder. No livestream kit or streaming operator is needed.
- Sam still handles delivery, now for 90 boxed lunches, below the 100-person limit.

| Quote line | Amount |
| --- | ---: |
| Alder | $900 |
| Birch | $600 |
| Boxed lunch: 90 × $18 | $1,620 |
| Presentation kit | $200 |
| **Total** | **$3,320** |

The enhanced-lunch alternative costs **$3,860**, leaving $140 below the cap.

### Revision comparison shown to the viewer

| Fact | Revision 1 practical option | Revision 2 practical option |
| --- | --- | --- |
| Attendance / breakout size | 60 / 30 each | 90 / 45 each |
| Rooms | Birch + Cedar | Alder + Birch |
| Room cost | $900 | $1,500 |
| Lunch cost | $1,080 | $1,620 |
| Technical equipment | Presentation + livestream: $500 | Presentation: $200 |
| Streaming staff | Lee: $300 | None: $0 |
| Total | $2,780 | $3,320 |
| Budget headroom | $1,220 | $680 |

Net change: **+$540**, comprising +$600 rooms, +$540 catering, −$300 livestream kit, and −$300 operator.

The technical specialist still has presentation equipment to check. Removing livestreaming should remove its requirements and unnecessary streaming-staff work; it should not remove all technical investigation. The separate room-only scenario demonstrates omission of that specialist altogether.

## Act 4: accept and see the result

Alex accepts revision 2's practical option at $3,320. The server verifies the current requirement revision, candidate/quote correspondence, prices, permissions, capacities, and availability under transaction protection.

On success, the UI shows a generated booking ID and **Booked — $3,320**. The schedule contains:

| Resource | New reservation |
| --- | --- |
| Alder | 12:30–18:30 |
| Birch | 12:30–18:30 |
| Presentation kit | 1 unit, 12:30–18:30, assigned to Alder |
| Sam / lunch delivery | 12:30–13:30, 90 meals |

There is no new Cedar reservation, livestream allocation, or Lee assignment. Revision 1 never held those resources, so nothing is released. Existing seed reservations remain intact. Repeated submission of the same acceptance must return the existing result or a clear already-booked response, never a duplicate booking.

No invoice, payment, email, or booking amendment follows. This is the end of the main demonstration.

## Optional branches for wireframe states and acceptance checks

Run each branch from its specified reset point; do not silently accumulate mutations across demonstrations.

| Branch | Setup/action | Expected behavior |
| --- | --- | --- |
| Simple meeting | Fresh request: 20 people, same date/block, no catering or AV | Cedar at $300 is feasible and cheapest. No catering or technical specialist is needed. |
| Unavailable livestream kit | Reset to revision 1 inputs; reserve the only streaming kit for another event | No option satisfies mandatory livestreaming. Explain the missing resource and ask whether the requirement can change. Do not silently omit it. |
| Budget versus preference | Revision 1 inputs, $3,000 cap, enhanced lunch preferred but boxed lunch acceptable | Practical option fits; enhanced lunch exceeds the cap by $140. Raise cap to $3,200 and reassess: enhanced option at $3,140 can now be preferred. |
| Restricted adjustment | Valid revision 2 practical option; fixed demo policy permits one $100 room credit when room subtotal is at least $500 | Alex is denied; Morgan can authorize the credit, producing a new quote of $3,220. Record actor and reason. Reapplication cannot stack credits. |
| Missing meal counts | Original brief without confirmed dietary counts | Show the specific question; retain a draft, with no bookable complete option. |
| Stale acceptance | After revision 2 assessment, a second request books Alder 12:30–18:30 | Acceptance fails clearly and writes no partial allocations. Reassessment explains that the remaining rooms cannot hold the required 90-person plenary. |

The credit is an application-owned fixed policy implemented in Java, with equivalent checks at any skill exposure. It does not make arbitrary model-requested discounts valid. The original quote remains in history; acceptance uses the current authorized quote.

Also run a transaction test where two requests concurrently accept conflicting Alder bookings. Exactly one can succeed. This verifies correctness without requiring another polished product screen.

Malformed model outputs, provider retries, and timeouts belong in the labeled developer harness and controlled tests described in the scope document, not the main walkthrough.

## What this establishes for the next step

Wireframes should cover confirmed intake, clarification, assessment in progress, priced proposals, revision comparison, booking success, stale acceptance, and denied adjustment. They should use the actual names, intervals, and amounts above.

Deterministic checks should verify the reference allocations, totals, capacity limits, boundary intervals, credits, and transaction behavior. Live model smoke checks should verify relevant specialist selection, a useful nested space plan, grounded structured results, and actual overlap when demonstrating concurrency. Neither a scripted trace nor exact model wording is an acceptance requirement.

This walkthrough settles fixture choices for the central story. It does not select frontend technology, finalize API schemas, create skill manifests, or establish that a model configuration has passed live testing.

