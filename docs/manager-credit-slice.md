# Working slice 04: manager room credit

This branch demonstrates Loomspan authorization and ordinary Spring method security around a deterministic write. It adds no model call and no new YAML skill: the application explicitly invokes the annotation-defined Java skill `applyRoomCredit` through `SkillTemplate`. The assessment planners do not expose the credit capability in their allowlists.

## Walkthrough

Use a current unbooked $3,320 proposal for 90 attendees, 75 standard/15 vegan lunches, presentation and no livestream. As **Alex · coordinator**, try **Apply $100 room credit**: HTTP 403, no credit saved. Select **Morgan · manager** and try again: the fixed $100 credit is recorded and payable total becomes **$3,220**. The $3,320 original quote and allocation prices remain unchanged. Accepting reserves the same five resources and stores the $3,220 booking total.

The selector is explicitly labeled **Demo identity — simulated login**. `X-Annex-Demo-User` maps only `alex` and `morgan` to server-defined roles, defaulting to Alex when absent. Unknown identity values are rejected. This is intentionally selectable impersonation for a loopback demo, not identity verification or production authentication. There is no login, password, account administration or session persistence. CSRF is disabled for this stateless header-based demonstration; do not deploy this identity adapter as production authentication.

## Rules and implementation

- Both the registered Java skill wrapper and the transactional application method have `@RolesAllowed("MANAGER")`; JSR-250 method security is enabled. The facade carries the request's authenticated identity. No model or business argument supplies an approver, role, or amount.
- `POST /api/proposals/{id}/room-credit` invokes only `applyRoomCredit(proposalId)`. It returns the recorded credit and a completed Loomspan session reference. Authorization exceptions remain failures, while eligibility failures are explicit structured decisions mapped to HTTP 409.
- A credit requires a current, READY, unbooked proposal and an original room subtotal of at least $500. Cedar's $300 meeting is ineligible. Requests against historical or booked proposals fail.
- V5 adds one credit row keyed by proposal ID, with a fixed 10,000-cent ($100) amount constraint, authenticated approver and timestamp. Repeated approvals return the same credit; credits never stack. There is no undo or discretionary amount.
- Credit approval takes the same series and assessment locks as booking/revision operations. Booking validates original prices, versions and availability, then subtracts the recorded credit. A credit does not bypass resource checks or rescue an otherwise infeasible assessment.
- Proposal responses retain `totalCents` as the original quote and add `credit` and `payableTotalCents`. The UI presents both; revision comparison includes the credit separately. A fresh assessment or requirement revision creates a new proposal with no inherited credit. The original credited proposal remains in its own history.

The credit action's session reference is shown after approval in the current browser session. The credit amount, approver and time persist across refresh/restart independently of Console availability.

## Validation record

The standard Maven package build passed: 34 tests passed and three opt-in live tests were skipped. Six new integration tests exercise actual Spring method security and the Loomspan facade, HTTP identity handling, fixed-amount enforcement, duplicate and concurrent approvals, ineligible/historical/booked proposals, and booking price checks after approval.

Browser verification used a separate port 8083 and `target/credit-ui-check` database. A real model assessment produced the expected $3,320 workshop proposal. Alex received the denial with no discount; Morgan approved the $100 credit and the UI showed the preserved original quote, $3,220 payable total, approver, timestamp and credit skill session. Alex then accepted the proposal, reserving five resources at $3,220. The desktop credit layout was visually inspected.

Restart verification retained the original $3,320 quote, Morgan's $100 credit, $3,220 payable and booking totals, and the same five-resource booking. The temporary verification server was stopped; the normal demo database was not used.

