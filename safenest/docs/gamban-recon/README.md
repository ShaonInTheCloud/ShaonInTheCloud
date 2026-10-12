# Gamban feature recon (reference for SafeNest blocking)

Recon of Gamban's public features, produced 2026-10-12 with the replica-recon method: public pages and help-center articles only, no account, no app install, no network capture, no source or private API inspection.

- `recon.md`: sources, inferred screens, flows, components, data model, what cannot be cloned, sizing. Screens marked "guess" were not observed.
- `features.csv`: feature matrix (feature, area, priority, original, clone, notes). The `clone` column starts at `no`; fill it in as SafeNest implements or deliberately skips each row. Rows marked `skip` are things Gamban owns (blocklist data, certifications, partner programs, brand and copy).

Related existing doc: `../research-gamban.md` (deeper engineering notes, Android permission model, independent evaluations). Where the two differ, prefer that document.

Clean-room rule: this records what Gamban does, not how it is built. Do not copy its copy, branding, prices or blocklist.
