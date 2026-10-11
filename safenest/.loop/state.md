# Loop state

One line per iteration. Newest at the bottom. The loop reads this first and never repeats an approach marked FAILED.

## Open
- intelligence database test does not apply the later RLS-performance policy migration; consider loading both in order.

## Log
- 2026-10-12 run 1, iter 0: manual baseline. CI gate green (67 web, 9 edge, 5 py, java core). intelligence/test/database.test.mjs FAILS: reads supabase/schema-intelligence.sql, file no longer exists (schema moved to migrations/20260929092635_gambling_intelligence.sql).
- 2026-10-12 run 1, iter 1: pointed intelligence/test/database.test.mjs at migrations/20260929092635_gambling_intelligence.sql. Gate PASSED (67+9+17 node, 5 py, java core 147 checks). Checker REJECTED first SKILL.md: never-touch list omitted the gate itself and production function paths. Fixed; checker ACCEPTED on re-review.
- 2026-10-12 run 1, iter 1b: checker step now cross-model: codex exec (GPT via Codex CLI) when installed, Claude subagent fallback otherwise. Reviewer for iter 1 was the Claude fallback (codex not installed in the cloud workspace).
