# Ledger — Fintech
A page the colour of ink on a bank's leather blotter: deep teal-navy, brass accents, mint for "money that is yours". It sells Quay, an invented treasury tool for small exporters (2–40 staff) that locks FX rates at invoice time and collects in the buyer's currency. The audience is a founder or finance lead who distrusts fintech gloss, so the page behaves like a statement: tabular numerals that settle into place, dated steps, a spread printed in plain figures. The hero's live ledger is the only theatrical moment; everything after it is rules, columns and monospace numbers.

## Tokens
- Base `#0a1a21`, base-2 `#0e2229`, surface `#122b33`, surface-2 `#173640`
- Line `rgba(170,215,205,.14)`, stronger line `rgba(170,215,205,.28)`
- Text `#e4efec`, secondary `#a4bcb6` (both pass 4.5:1 on base and surface)
- Accent brass `#e8c170` with ink `#1d1708` on filled buttons; mint `#8fe0c0` for the balance curve and spreads; red `#f09a8a` reserved for negatives (unused in the demo)
- Display and body: Archivo, variable (width 88–125, weight 300–700). Headlines use `"wdth" 92`. Archivo ships proper `tnum` figures, and `font-feature-settings: "tnum" 1` is set on `body` so every number in prose lines up.
- Numbers: JetBrains Mono 400/500 for ledger cells, prices and figures. A monospace face is tabular by construction, so count-ups never jitter.
- Max width 1120 px, gutter `clamp(16px, 4vw, 48px)`, 8 px button radius, 14 px panel radius

## Layout
1. **Nav** — sticky, 62 px, translucent base with blur. Brand mark (warehouse-roof glyph with a brass door) + "Quay" left, four links centre-right (hidden under 720 px), ghost "Open an account" right.
2. **Hero** — two columns 0.95fr / 1.05fr, vertically centred, 88 px top padding. Left: small label "Treasury for exporters with 2 to 40 staff", h1 with the word "today" in brass, a 36-ch lede, filled CTA with a caption "Approved in two business days". Right: the ledger panel — a header row, a four-column table (Invoice, Buyer with city, Amount in buyer currency, Locked EUR), a brass-tinted total row, and below it a small SVG balance chart with a dashed unhedged reference line and a solid mint hedged line with gradient fill. Under 900 px the ledger drops below the text; under 480 px the Buyer column is hidden so three columns fit in 343 px.
3. **Proof band** — full-bleed strip on base-2 with top and bottom rules, four ruled cells: 0.18% spread (digits in brass), 31 currencies, T+1 settlement, 1,400 exporters. Monospace figures, 24-ch captions.
4. **How it settles** — h2 and paragraph capped at 60 ch, then a dated four-stage timeline: a horizontal rule with brass-ringed dots, each stage with a monospace date label ("Day 0", "Next business day"), h3 and paragraph. Under 860 px the rule turns vertical on the left.
5. **Product** — four features in a two-column grid, each with a top rule, an h3 carrying a small monospace qualifier ("up to 180 days", "14 currencies") and a 44-ch paragraph.
6. **Pricing** — one bordered panel split into three tiers by vertical rules (not three cards): Starter €0 at 0.35%, Growth €49 at 0.18% on the surface colour, Scale quoted from 0.09%. Monospace prices, mint spread line, dash-marked feature lists. Stacks with horizontal rules under 860 px.
7. **Final CTA** — a bordered box with a diagonal surface-to-brass gradient, h2 and paragraph left, filled button right.
8. **Footer** — regulatory line (licence number, segregated funds) left, four links right.

## Motion
- **Hero ledger (the one memorable moment).** The `.ledger` panel is observed with IntersectionObserver at 35% visibility. On entry it gets the class `on` and three things happen together:
  1. Each `tr.row` fades in and rises 6 px over 0.5 s with `cubic-bezier(.2,.7,.2,1)`, staggered 0.05 / 0.17 / 0.29 / 0.41 / 0.53 s.
  2. Every `[data-n]` cell counts from 0 to its value over 1.5 s with ease-out cubic, each starting 110 ms after the previous one (the total row waits an extra 700 ms so it lands last). Values are formatted with `Intl.NumberFormat('en-IE')` at the cell's `data-d` decimals, in JetBrains Mono so columns do not shift while counting.
  3. The hedged balance line (`pathLength="1"`, `stroke-dasharray: 1`) draws from left to right by transitioning `stroke-dashoffset` 1 → 0 over 1.8 s after a 0.35 s delay; the gradient fill fades in at 1.6 s and the end dot at 2.1 s.
- Nothing else animates on the page.
- **Reduced motion:** the script writes final values immediately and adds `on`; CSS overrides make rows, line, fill and dot visible with no transition. The result is a complete, static ledger. Smooth scrolling is off.

## Rebuild instructions
1. One HTML file. Link Archivo (ital, wdth 88..125, wght 300..700) and JetBrains Mono (400, 500) from Google Fonts. Put all colours on `:root`. Set `font-feature-settings: "tnum" 1` on `body` and a `.num` utility that switches to the mono face with `font-variant-numeric: tabular-nums`.
2. Sticky `<header>` with `<nav aria-label="Primary">`: brand link containing an inline SVG mark, a `<ul>` of four links (hidden under 720 px) and a ghost button link.
3. Hero section: a two-column grid. Left column is the label, `<h1>` (one brass `<b>`), lede, and a flex CTA row. Right column is `<div class="ledger" id="ledger">` with: a `.top` header row; a `<table>` whose `<tbody>` has five `tr.row` rows (invoice number in mono, buyer name with a `<span class="co">` city line, amount cell with a `.ccy` code and a `<span data-n="48250" data-d="2">0.00</span>`, locked-EUR cell with another `data-n` span); a `<tfoot>` total row with `data-delay="700"`; then a `.curve` block.
4. Curve block: a label row, then an SVG `viewBox="0 0 520 110"` containing a `<linearGradient id="cg">` (mint, 22% → 0%), a dashed `path.ref` for the unhedged reference, a `path.fill` (the hedged path closed down to the baseline), the hedged `path.line` with `pathLength="1"`, and a `circle.dot` at the line's end. Give the SVG `role="img"` and an `aria-label` describing the trend.
5. CSS for the panel: 1 px stronger-line border, 14 px radius, vertical surface→base-2 gradient, large soft shadow. Rows start `opacity: 0; transform: translateY(6px)`; `.ledger.on tr.row` transitions them in with the staggered delays. `.line` starts `stroke-dasharray: 1; stroke-dashoffset: 1` and `.ledger.on .line` transitions the offset to 0; `.fill` and `.dot` start at opacity 0 and transition in with their delays. Under 480 px hide column 2 (`nth-child(2)` on `th` and `td`) and reduce cell padding to 12 px.
6. Script (about 45 lines): collect `[data-n]` cells; `settle()` writes formatted final values; `count()` assigns each cell a start time `now + delay + i*110` and runs a `requestAnimationFrame` loop applying `1 - (1-p)^3` easing to `value * e` and formatting it. If `prefers-reduced-motion: reduce` matches or IntersectionObserver is missing, call `settle()`, add `on` and stop. Otherwise observe the panel at `threshold: .35`, and on first intersection add `on`, call `count()` and disconnect.
7. Proof band: a full-width `<section>` on base-2 with top/bottom rules; inside the wrap, a four-column grid of plain `<div>`s with left rules between them, a mono `<strong>` figure and a `<span>` caption. Collapse to 2 then 1 column, swapping left rules for top rules.
8. How it settles: a capped heading block, then `.flow` as a four-column grid with a `::before` horizontal rule at 11 px from the top; each `.stage` has a `::before` 11 px dot with a 2 px brass border, a mono `.day` label, h3 and paragraph. Under 860 px, rotate the rule vertical at left: 5 px and pad the grid 22 px on the left.
9. Product: a two-column grid of `<article>`s with top rules; the h3 holds a small mono qualifier span.
10. Pricing: a single `.tiers` bordered panel as a three-column grid; each `.tier` is a flex column with h3, "for" line, mono price with a small qualifier, a mint mono spread line, a dash-marked list and a button pinned to the bottom. The lead tier takes the surface background. Stack under 860 px with top rules.
11. Final CTA: a bordered box with a diagonal gradient (surface → base-2 → 8% brass), two-column grid with the text left and the filled button right; single column under 760 px.
12. Footer: ruled top, flex row with the regulatory paragraph (max 52 ch) and a four-link list including a mailto contact.
13. Add `:focus-visible` outlines in brass, smooth scrolling with the reduced-motion override, and `overflow-x: hidden` on body as a guard.
14. Check at 375 px: ledger shows three columns without wrapping, the timeline is vertical, tiers are stacked, and the total row still lands last in the count-up. Confirm one `<h1>` and landmarks header, nav, main, footer.
