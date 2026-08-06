# Change summary template

Fill one of these in for **every** version bump of the Privacy Policy or the Terms. The filled copy
goes in `legal/archive/` next to the superseded document, and its "What changed" section is what the
in-app re-acceptance screen shows.

Write it for the person who has to decide whether to accept. Not a diff, not a legal note — the
answer to "does this change anything about my files?"

---

## Document

| | |
|---|---|
| Document | Privacy Policy / Terms of Service |
| Previous version | |
| New version | |
| Previous effective date | |
| New effective date | |
| App version code at release | |

## Is re-acceptance required?

**Required** when any of these is true:

- [ ] Terms materially change
- [ ] Data-collection practices materially change
- [ ] A new cloud-processing feature is introduced
- [ ] New third-party data sharing is introduced
- [ ] The legal basis or the user's obligations materially change
- [ ] Required acceptance data is missing or corrupted

**Not required** — and must not be triggered — for:

- [ ] Typographical corrections
- [ ] Formatting or layout changes
- [ ] Contact-address updates
- [ ] Clarifications that do not change rights or processing

> If every box in the second list is ticked and none in the first, set `requiresReacceptance: false`.
> Prompting people for a comma is how they learn to dismiss the prompt that matters.

**Decision:** re-acceptance required — yes / no
**Reason:**

## What changed — plain language

Three to six bullets, in the words a user would use. State the effect, not the section number.

- …
- …

## What did not change

Say this explicitly. It is usually the part people actually want to know.

- …

## Code changes that made this necessary

| Change | File / feature | Inventory row updated? |
|---|---|---|
| | | |

Every row here must have a matching update in `legal/data-practices-inventory.md`. A policy change
with no code change, or a code change with no inventory update, is a sign that one of the two is
wrong.

## Consistency check before release

- [ ] `legal/data-practices-inventory.md` updated
- [ ] `legal/privacy-policy.md` updated
- [ ] `legal/terms-of-service.md` updated
- [ ] `legal/plain-language-privacy-summary.md` updated
- [ ] `legal/play-data-safety-map.md` updated
- [ ] `assets/legal/*-v<new>.html` generated and bundled
- [ ] `legal/public/*.html` regenerated and published
- [ ] Previous version copied to `legal/archive/`
- [ ] `legal/legal-config.json` versions and dates updated
- [ ] Play Console Data Safety form updated to match
- [ ] Store listing still accurate
- [ ] Human legal review completed for every target market

## Legal review

| | |
|---|---|
| Reviewed by | |
| Date | |
| Markets covered | |
| Outstanding concerns | |
