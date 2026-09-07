# Implementation status and release preparation

The five functional slices are implemented. The next work is making the demo reproducible and reviewable for other developers. Use [the developer walkthrough](demo-walkthrough.md) for the presenter sequence and [scope](scope-and-demo-design.md) for the first-version boundary.

## Completed slices

| Slice | Result | Detailed record |
| --- | --- | --- |
| 1. Room-only meeting | React → REST → Loomspan → Hibernate/H2; $300 Cedar quote and transactional booking | [Initial implementation history](history/initial-room-only-slice.md) |
| 2. Workshop | Space/catering/technical specialists, validated $2,780 quote, multi-resource booking | [Workshop](workshop-slice.md) |
| 3. Requirement revisions | Immutable revisions and stored comparison, $2,780 → $3,320; historical/stale guards | [Revisions](revision-slice.md) |
| 4. Manager credit | Alex denied; Morgan approves one $100 credit; $3,220 payable | [Credit](manager-credit-slice.md) |
| 5. Brief and agenda intake | Attachment-aware interpretation, missing-value review, durable sources and explicit confirmation | [Intake](intake-slice.md) |

The latest functional milestone recorded 41 ordinary passing tests and two passing live intake tests. Earlier slice records document their own live assessment, concurrency, browser and restart checks. These are milestone results, not a claim that every live test or the complete runbook was rerun during documentation cleanup.

Tested framework revision: `d202b204ea41a9cfee0364221888df157b469bc3`, starter `0.1.0-SNAPSHOT`; Java 21, Spring Boot 4.1.0, Spring AI 2.0.0, default `gpt-4.1` model configuration. A SNAPSHOT name alone does not pin reproducible content.

## Release preparation

| Work | Status | Acceptance |
| --- | --- | --- |
| Developer walkthrough and scope reconciliation | Written; checked against application code and slice records | Exact controls/inputs, totals, source entry points, optional inspection and known boundaries |
| Fresh-checkout verification | Pending | Isolated checkout and Maven repository; install the documented framework revision, build, start, use intake/assessment, verify restart and explicit reset; record toolchain and results |
| Combined presenter and Console rehearsal | Pending | Run the complete unbooked-revision-credit-booking sequence; locate actual sessions and inspect overlap without relying on a scripted trace |
| CI | Pending | Explicit framework checkout/install at a pinned revision, then demo build and ordinary tests; live model tests opt-in with external secrets |
| Repository distribution review | Pending | Verify tracked files, licensing, example image and documentation; exclude databases, uploads, credentials, logs, build artifacts and the nested prototype repository |
| GitHub distribution/release | Review pending; origin is configured | Review the standalone repository at `https://github.com/loomspan/loomspan-venue-demo.git`; any push or release is a separately authorized action |

Do not claim fresh-checkout support based only on a build against the existing developer's Maven cache. A later framework install can replace the same SNAPSHOT coordinates; record the commit used for verification.

## Next execution checklist

1. Create isolated verification directories and a separate Maven local repository. Leave the user's checkout, installed artifacts and demo data intact.
2. Obtain the documented framework revision and install its starter dependencies there, then build a clean demo checkout using that repository.
3. Follow the README exactly, with model credentials supplied externally. Use fresh business data and a separate port if another demo is running.
4. Rehearse the developer runbook, optional Console inspection, restart and explicit reset. Record successful and failed checks honestly.
5. Resolve documentation/setup gaps, then prepare CI and the repository for review.

This checklist specifies future work; documentation cleanup did not run it. Additional proposal cards, generated agendas and production venue features remain out of scope.
