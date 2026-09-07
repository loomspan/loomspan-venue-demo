# Working slice 03: requirement revisions

An unbooked event can receive a new confirmed requirement revision. Earlier inputs, assessments and proposal allocations remain immutable and readable. This demonstrates fresh Loomspan planning against changed facts, without introducing booking cancellation or rescheduling.

## Walkthrough

1. Assess the 60-person workshop with 50 standard and 10 vegan lunches and livestreaming. Keep the $2,780 Birch + Cedar proposal unbooked.
2. Choose **Revise requirements**. Change attendance to 90, explicitly change lunches to 75 standard and 15 vegan, and clear livestream. Keep presentation enabled. Confirm and save the new revision.
3. The former proposal becomes historical and cannot be accepted. Assess the current revision through the existing Loomspan skills.
4. Compare the original $2,780 proposal with the $3,320 Alder + Birch proposal: rooms +$600, lunch +$540, livestream kit −$300, streaming operator −$300, net **+$540**. Presentation remains $200. No streaming kit or operator is reserved for revision 2.
5. Accept only the current proposal; one booking reserves its five resources. Booking makes further requirement revision unavailable.

Meal counts are never automatically scaled. A failed or infeasible new assessment does not reactivate an old proposal. Revision history remains available after refresh/restart. Each revision can retain its own assessment retries; cost comparison uses the previous revision's latest validated proposal and the selected revision's latest assessment when it has a proposal. Missing quotes are displayed as unavailable, not zero.

## Storage and HTTP contract

V4 adds `series_id` and `revision_number` to existing immutable request rows. Existing requests become revision 1 of their own series; V1–V3 are unchanged. A unique constraint prevents duplicate revision numbers in one series. The original request ID is the series ID and shared lock anchor.

- `POST /api/events/{id}/revisions` accepts the same fully confirmed, validated body as event creation and returns the new revision. The URL identifies the revision being replaced, so a stale editor receives HTTP 409. Event type cannot change within a series.
- `GET /api/events` returns immutable revisions with `seriesId`, `revisionNumber` and a derived `current` flag. The sidebar lists current requests, with revision navigation inside the workspace.
- Existing assessment and acceptance routes reject historical revisions with HTTP 409. Booking and revision creation lock the same series anchor before checking currentness, preventing both from succeeding in a race.
- Revisions are rejected during a running assessment. No database transaction spans model execution. All specialist calls continue to use the immutable request ID for their specific revision; no new YAML skills or model API integration are needed.

Price comparison reads persisted allocations and totals. It never reruns a quote against today's inventory to reconstruct an earlier result. The UI disables historical actions; the server independently enforces the same rules.

## Scope limits

Structured editing only; no conversational change extraction, branch/merge, undo, automatic meal scaling, amendment of accepted bookings, cancellation, or rescheduling. The separate wireframe remains a simulated reference for later features.

## Validation record

The standard Maven package build passed with 28 ordinary tests and three opt-in live tests skipped. The separate `LiveRevisionTest` passed using real model assessments for both the $2,780 original and $3,320 revised proposal. Existing room-only/workshop regression tests, migration of pre-existing records, historical-proposal rejection, invalid meal counts, in-flight assessment blocking, simultaneous revisions, booking-versus-revision races, and infeasible revisions are covered.

Browser verification on a separate port 8082 and `target/revision-ui-check` database exercised creation and assessment of revision 1, prefilled editing, explicit meal-count correction, removal of livestream, confirmation, and assessment of revision 2. Historical assessment/acceptance actions were disabled. The comparison displayed the expected +$540 total and all four contributing changes. Revision 2 booked five resources under booking `0102a872-8bbe-4f3f-97fe-6726ecbc8a48`, after which revision editing was disabled. Desktop layout was visually inspected. The user's normal demo database was not used for verification.

Restart verification retained both requirement revisions, their original quote totals, current/historical flags and the same five-resource booking. An HTTP acceptance attempt against the historical proposal returned 409. The temporary verification server was stopped.
