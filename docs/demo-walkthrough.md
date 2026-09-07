# Developer walkthrough: The Annex

This is the runnable demonstration of the implemented application. Allow about 10–15 minutes plus model latency. Use [the README](../README.md) for installation and [scope](scope-and-demo-design.md) for product boundaries. The older simulated wireframe is not the running application.

The story is one event, two requirement revisions, two assessments, a manager credit and one booking. Do not book revision 1: acceptance would prevent the revision step.

## Before presenting

- Build and start the packaged application using the README. Open [The Annex](http://localhost:8080). Configure model access; the application does not fabricate results when a model fails.
- Use fresh fixture availability. On an existing demo, stop the application and deliberately run `./scripts/reset-demo.ps1 -ConfirmReset` before restarting. This deletes default local intake/event/booking data and app-owned agenda images. Do not reset data you want to retain. Custom database and attachment locations require their own explicit reset.
- Select **Alex · coordinator**. The selector is a simulated identity switch, with real server-side role enforcement for the credit.
- The intake reference date is **October 8, 2026**; “next Thursday” resolves to **October 15, 2026** in Pacific time. These are fixed demo dates, independent of today's date.
- If demonstrating Console, connect it before starting these executions; see the optional section below. The business walkthrough works without it.

Expected checkpoints on fresh fixtures:

| Stage | Original quote | Credit | Payable | Reservation result |
| --- | ---: | ---: | ---: | --- |
| 60-person workshop | $2,780 | — | $2,780 | Nothing reserved |
| 90-person revision, no livestream | $3,320 | — | $3,320 | Nothing reserved |
| Morgan approves | $3,320 | −$100 | $3,220 | Nothing reserved |
| Alex accepts | $3,320 | −$100 | $3,220 | Five resource reservations |

## 1. Interpret the brief and agenda

Choose **New request**, then **Use example agenda** and **Interpret with Loomspan**. Keep the default brief: 60 attendees, presentation, two equal breakout groups, lunch with vegan options, livestream and a $4,000 budget.

Open **Source brief and agenda**. The image is last year's agenda: 40 attendees and 35 standard/5 vegan lunches. Its session titles include “Product roadmap presentation” and “Customer Q&A.”

Check that the review form uses 60 attendees and October 15, 2026. Current meal counts should be blank. Enter **50 standard** and **10 vegan** lunches. Review any interpretation notes and confirm presentation and livestream. Check the explicit requirements confirmation, then choose **Save request**.

Explain: “The model proposes structured requirements from text and an image. Unknowns remain for a person to resolve. Java also rejects ungrounded dietary counts. Confirming creates an event; it does not assess or reserve anything.”

Inspect [interpret-event-brief.yml](../src/main/resources/skills/interpret-event-brief.yml) and [IntakeService.java](../src/main/java/demo/annex/IntakeService.java). This direct YAML skill has an optional attachment input, nullable output fields and no child tools. The `intake` model alias is independently configurable. The source and validated interpretation persist separately from corrected requirements.

## 2. Assess the 60-person workshop

Choose **Assess with Loomspan**. Wait for **READY** and **A validated event proposal**. Expected result: **Birch Room + Cedar Room**, $2,780.

| Quote component | Amount |
| --- | ---: |
| Birch + Cedar | $900 |
| 60 boxed lunches × $18 | $1,080 |
| Presentation kit | $200 |
| Livestream kit | $300 |
| Lee: six streaming-operator hours × $50 | $300 |
| Sam: catering attendant, included | $0 |
| **Total** | **$2,780** |

Birch seats the 60-person plenary; Birch and Cedar each hold one 30-person breakout group. Expand **Supporting records and reservations** to see resource IDs, versions, quantities and intervals. These are proposed allocations, not holds. **Do not accept yet.**

Explain: “Loomspan coordinates narrowly scoped specialists. Java checks the actual catalog, capacity, availability and exact cost. The application returns one least-cost validated proposal.”

Inspect [assess-event.yml](../src/main/resources/skills/assess-event.yml). Its workshop prompt guides space and catering into the independent `investigations` group; technical work needs the chosen plenary room, and the final quote depends on specialist results. [plan-event-space.yml](../src/main/resources/skills/plan-event-space.yml) is a nested planner. Catering and technical YAML skills use direct execution with Java children. This is bounded, guided planning over a small catalog, not unconstrained scheduling.

## 3. Revise the unbooked request

Choose **Revise requirements**. Set attendance to **90**, standard lunches to **75**, vegan lunches to **15**, and clear **Livestream plenaries, including operator**. Keep presentation and the $4,000 budget. Explicitly confirm and **Save new revision**, then **Assess with Loomspan** again.

Expected result: **Alder Hall + Birch Room**, $3,320. The comparison shows:

| Change | Cost difference |
| --- | ---: |
| Larger room assignment | +$600 |
| 30 additional boxed lunches | +$540 |
| Remove livestream kit | −$300 |
| Remove streaming operator | −$300 |
| **Net change** | **+$540** |

Open revision 1 to show its stored $2,780 quote and disabled historical assessment/acceptance actions. Return to revision 2.

Explain: “The application owns immutable requirement revisions. Each assessment is a fresh invocation. Comparison reads stored quotes; it does not rewrite the old result. Removing livestream removes its resources, but presentation still needs technical checks.”

This step uses the structured edit form, not a conversational change command. See [revision-slice.md](revision-slice.md).

## 4. Demonstrate manager authorization

On the current unbooked proposal, choose **Apply $100 room credit** as Alex. Expect the denial message and no discount.

Select **Morgan · manager** and repeat. Expect the original quote to remain $3,320, one $100 credit with approver/time, and **$3,220 payable**. The credit skill's session reference appears after approval.

Explain: “This action invokes a Java skill through the same Loomspan facade, with no model call. Spring method security enforces the manager role. The UI cannot supply the amount or approver as business arguments.”

Inspect [CreditSkills.java](../src/main/java/demo/annex/CreditSkills.java) and [DemoSecurity.java](../src/main/java/demo/annex/DemoSecurity.java). The fixed credit requires at least $500 in room charges and a current, READY, unbooked proposal. It cannot stack or bypass stale resource checks. Identity selection is deliberate local impersonation, not production login. See [manager-credit-slice.md](manager-credit-slice.md).

## 5. Accept and verify persistence

Switch back to **Alex · coordinator** and choose **Accept & reserve all resources**. Expect **Booking saved** at $3,220 with five allocations:

| Resource | Reserved interval on October 15 |
| --- | --- |
| Alder Hall | 12:30–18:30 |
| Birch Room | 12:30–18:30 |
| Presentation kit, one unit | 12:30–18:30 |
| Boxed lunch, 90 portions | 12:30–13:30 |
| Sam, catering attendant | 12:30–13:30 |

There is no new Cedar, livestream-kit or Lee reservation. The original proposal never held those resources. The seeded Cedar meeting ends at 12:30 and Alder reception starts at 18:30; half-open intervals allow these adjacent reservations.

Choose **Refresh records**. Optionally stop and restart the application without reset, reopen the saved request, and verify the same booking, credit, source agenda and requirement history.

Explain: “Acceptance is an explicit transactional application action, not an agent tool. Java locks and rechecks the proposal's currentness, original prices, versions and resource availability before committing the booking and all allocations.”

## Optional short branches

Run branches separately from the main story so reservations do not unexpectedly change its expected prices.

| Branch | Exact setup | What it demonstrates |
| --- | --- | --- |
| Room-only meeting | New request → event type **Room-only meeting**; October 16, 2026; 20 attendees; $500; confirm and assess | Cedar at $300; space planning without catering or technical specialists |
| Budget infeasibility | New workshop on October 16; default 60/50/10 inputs and both technical services, but budget $2,000 | No validated option fits; no silent service removal or booking |
| Ineligible credit | Current, unbooked $300 Cedar meeting proposal; try the credit as Morgan | Manager permission does not override the $500 room-subtotal rule |
| Stale acceptance | On unused October 17, create two room-only requests for 20 people/$500; assess both before accepting either, then accept the first and attempt the second | Both may quote Cedar, but the second acceptance conflicts and cannot double-book it; reassess |

The test suite also covers competing bookings, revisions and credits, changed prices, malformed output and provider failures. There are no live-app fixture buttons for “unavailable livestream,” no enhanced-lunch preference selector and no fault-injection screen. Those broader wireframe ideas are not steps in this runbook.

## Optional Console inspection

Use a Console build aligned with the tested framework revision. See the framework's [Console README](https://github.com/loomspan/loomspan-framework/blob/d202b204ea41a9cfee0364221888df157b469bc3/loomspan-console/README.md) for installation/build prerequisites; Console is not part of this demo's Maven build.

Before starting the application, set `ANNEX_OBSERVABILITY_ENABLED=true` and `ANNEX_OBSERVABILITY_API_KEY` to a separately generated random key: at least 32 random bytes encoded as unpadded base64url, yielding 32–512 printable non-whitespace characters. Do not use the model API key.

For a presentation where successful traces must remain inspectable, start the app with:

```powershell
java -jar target/the-annex-0.1.0-SNAPSHOT.jar --execution-trace.persistence=ALWAYS
```

The normal configuration is `ONERROR`; successful sessions do not imply a persisted trace under that policy. Start recording before the demo; changing persistence later does not reconstruct earlier executions. Trace availability and retention are separate from H2 business persistence.

In Console, pair the browser if prompted, select target `http://127.0.0.1:8080`, provide the same application observability key, and choose **Connect**. An installed Console executable can prefill the address with `--target-address http://127.0.0.1:8080`; that flag does not connect automatically. The read-only application API is under `/_loomspan/observability/v1/**`.

Use the session references displayed by intake and assessment to locate those executions. Capture the transient credit session reference immediately after approval if inspecting it; only its business approval record persists in the application.

Inspect these questions:

1. Did intake receive an image attachment and produce nullable structured requirements through the `intake` alias?
2. Does the workshop execution contain the root and nested space plans, plus catering, technical and Java quote calls?
3. Did space and catering actually overlap? A `parallelGroup` and effective concurrency show scheduling intent; observed start/end intervals or a recorded overlap result establish what happened. If that evidence is absent, do not claim measured concurrency or speedup.
4. Does revision 2 omit livestream equipment/operator requirements while retaining presentation checks?
5. Does the credit execution use the authenticated manager and complete without a model call?
6. Were retries recovered, or did a failure terminate the execution? Do not present intermediate errors as the final outcome.

The UI's session ID is a lookup reference, not proof that every trace is available. Console setup and this complete presenter sequence have not yet been rehearsed together from a fresh checkout; the slice documents record the live tests and browser checks actually performed.

## If the demo needs attention

| Symptom | Check or recovery |
| --- | --- |
| Expected $2,780/$3,320 result changes or becomes unavailable | Check existing reservations and confirmed counts/date. Reset deliberately or use an unused date; do not change business validation to force the expected result. |
| Intake leaves fields blank or flags a conflict | Review the source and fill supported requirements explicitly. Model interpretation still needs human review. |
| Intake/assessment fails | Check model configuration and optional Console diagnostics. No fake successful result is supplied; retry after resolving the cause. |
| Maven cannot resolve the starter | Install the documented framework revision into the Maven repository used by the demo. |
| Maven cannot replace the JAR on Windows | Stop the process running that JAR before rebuilding. |
| Browser shows a missing page or stale UI | Build with `mvnw.cmd package`, restart that JAR, and refresh. For Vite development, use its printed browser URL. |
| Console cannot connect or has no matching trace | Check target, key, version alignment, persistence mode and trace availability separately. Business records remain usable without Console. |
