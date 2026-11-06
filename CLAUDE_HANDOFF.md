# QA Job-Hunt Handoff — Md Humaun Kabir (Shaon)

Paste this into a new Claude Code session, or keep it in the repo next to `CLAUDE.md`.

## Who I am
- ICT student, JAMK Jyväskylä (240 cr, ~halfway). Earlier Automation & Robotics track.
- Completed (Metropolia Open UAS): Software Testing Automation with Robot Framework, Python, Python & Data Analytics, Linux. Also UiPath RPA, SQL, Scala basics.
- Shipped products: SafeNest (Android gambling-blocker + mysafenestbd.com), CricNation (cricket scoring web app), cricket broadcast overlay, Sugar Cascade (match-3 browser game).
- Registered sole trader (Business ID 3433498-1). Fast bowler, Finland national cricket team.
- GitHub: github.com/ShaonInTheCloud (SafeNest under safenest/)
- English fluent, Finnish basic. Based in Jyväskylä.

## Goal
Land an English-speaking QA / Test Automation Engineer role in Finland within ~3 months. 11 focused hours/day, 14-day sprint then keep applying.

## Target stack
Python, pytest, Playwright (Python), Robot Framework, API testing (requests/Postman), SQL, GitHub Actions, Docker, ISTQB Foundation. Plus LLM eval basics (promptfoo/DeepEval) as a differentiator.

## Applications: live tracker
Verified job list with status dropdowns (checked 6 Nov 2026 by opening every posting):
https://claude.ai/artifact/1awpDTNfB34dwY83LyagfS

Priority order for the first week (all English postings, junior-friendly):
1. Vaisala via Academic Work — Junior Software Test Engineer, Vantaa. Robot Framework, Jenkins, Python. Rolling, may close early.
2. Kassavirtanen — Junior Software Tester, Helsinki. Deadline 13 Nov.
3. Bluefors — Trainee, Test Systems & Data, Helsinki. Deadline 12 Nov. For students.
4. Nordic Semiconductor — Firmware Test Engineer, Secure Domain, Oulu. Early-career stated. Python + CI; C is the gap.
5. Visual Components — Junior Software QA Engineer, Espoo/Tampere.
6. Lokki (banking client) — QA Engineer mobile+web, Helsinki. Deadline 9 Nov, asks 3 yrs; stretch.
7. Open applications: Siili (VALA merged into Siili in 2024), Gofore, Nitor, Vincit.

If Finnish is workable: NICO junior tester (deadline 10 Nov), OP Test Engineer Oulu/Helsinki (15 Nov), Academic Work software validation engineer in Jyväskylä (rolling), Tieto Tech Consulting Test Automation Specialist (rolling).

### Corrections to the 6 Nov LinkedIn triage
- VALA Group no longer exists as a separate company; it merged into Siili Solutions in 2024. vala.fi is an unrelated organisation. Apply via the Siili open application.
- Gofore's Test Automation Developer posting is gone; only embedded test roles (several years' experience) remain.
- Sine Engineering QA Engineer (Tampere) closed on 1 Nov 2026.
- Tieto Tech Consulting's January Jyväskylä posting expired; the live one is the 29 Oct Espoo re-post, Finnish required.
- ALTEN's Software Test Engineer posting is stale; only the Finnish-language open application for 3+ yrs is live.
- NICO junior tester is confirmed open to 10 Nov and mandates good written Finnish.

## Portfolio projects to build (all in github.com/ShaonInTheCloud)
1. `qa-playwright-demo` — Playwright+Python suite vs saucedemo.com, Page Object Model, 25+ tests, HTML report, GitHub Actions.
2. `qa-api-tests` — pytest+requests vs a public API, schema validation, CI.
3. `safenest-robot-tests` — Robot Framework suite for SafeNest website/app, CI.
4. `llm-eval-demo` — small chatbot + promptfoo eval suite in CI.
Optional for Sine: pytest against PX4 SITL over MAVLink.

## 14-day plan
| Day | Morning 4h | Afternoon 4h | Evening 3h |
|---|---|---|---|
| 1 | Apply VALA, Tieto, Sine | Playwright setup, locators | pytest basics |
| 2 | Playwright waits/assertions | Page Object Model | Python practice |
| 3 | Project 1 build | Project 1 25+ tests | README, push |
| 4 | REST/API theory, Postman | pytest+requests | SQL joins |
| 5 | Project 2 | Schema checks, reports | Python practice |
| 6 | Robot Framework deep dive | Project 3 | Push, polish |
| 7 | Review weak spots | Half day off | Sleep early |
| 8 | GitHub Actions for all 3 | Docker basics | Badges/README |
| 9 | ISTQB ch 1–3 | ISTQB ch 4–6 | Mock exam |
| 10 | Project 4 | Wire into CI | Write-up |
| 11 | Timed Python coding tasks | Explain-your-framework drills | Python practice |
| 12 | Mock interview on camera | Fix gaps | Update CV + LinkedIn |
| 13 | Apply to 10 more | Call recruiters: Academic Work, Eficode, Siili | Testing scenarios |
| 14 | Final repo polish | Apply to 10 more | Plan weeks 3–4 |

Rules: commit daily; never list a skill on the CV that isn't backed by a repo; 7.5h sleep is non-negotiable.

## CV
Current CV lives in Claude Docs: https://claude.ai/artifact/LxRq7DzTKthDxH5G9m9wQA
Fill in: email, phone, LinkedIn URL, sole-trader start year.

## How to use this with Claude Code
Start with: "Read CLAUDE_HANDOFF.md. Today is Day N. Set up Project 1 as described."
