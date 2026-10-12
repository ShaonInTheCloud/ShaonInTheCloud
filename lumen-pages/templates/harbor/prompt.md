# Harbor — Hospitality / Travel
A night-harbour landing page for a small inn or sailing charter. The whole page sits in a deep sea-blue ink, and the only light on it comes from a handful of warm quay lamps whose reflections move on the water behind the headline. It is for a hospitality brand that sells atmosphere and specifics rather than amenities lists: a seven-room inn with its own boat, a charter skipper, a lighthouse cottage. The copy is plain and particular (tide times, what to bring, who runs the place), the booking panel is a real form, and nothing below the hero competes with it.

## Tokens
- Base ink `#0a1726`; raised surface `#0e1f33`; sky at the horizon `#132a44`
- Hairline `rgba(190,205,225,.16)`
- Text `#e7edf4`; muted text `#aebbcb`
- Lamp accent `#f2b56b` (hover `#ffc98a`); text on lamp `#1a1206`; one cold lamp `#d7e2f7`
- Canvas colours are read from `:root` as RGB triplets: `--sky-top-rgb 10,23,38`, `--sky-hz-rgb 22,44,72`, `--sea-rgb 8,19,32`, `--quay-rgb 6,13,22`, `--lamp-rgb 255,186,110`, `--cool-rgb 215,226,247`
- Display: Cormorant Garamond 500 and 600, italic 500 (headings, prices, times, the kicker). Chosen because a high-contrast old-style serif reads like a ship's log or a printed tide table, and its italic carries the "at first light" emphasis without any colour tricks.
- Body: Alegreya Sans 400, 500, 700, italic 400. A humanist sans with a calligraphic skeleton that sits comfortably next to a Garamond, stays warm at 17 px on a dark ground, and has proper tabular figures for the tide table and rate card.
- Body size 17 px, line height 1.55, tabular numerals on everywhere.

## Layout
Content width `min(1120px, 100% - 2 × gutter)`, gutter `clamp(16px, 4vw, 40px)`.

1. **Nav** is absolutely positioned over the hero: brand with a small lantern glyph on the left, four links in the middle (hidden under 760 px; they repeat in the footer), and a ghost "Book a stay" link on the right that anchors to the booking panel.
2. **Hero** is `min-height: 100svh`, flex column, content pushed to the bottom. The canvas fills the hero behind everything. Above the horizon: an italic serif kicker in lamp colour, the h1 at `clamp(2.55rem, 5.6vw, 4.3rem)` and 19 characters wide with the last three words in italic lamp colour, a muted lede of about 60 characters, then a deliberate gap of `clamp(6rem, 19vh, 12rem)` where the water and its reflections are fully visible. Over the water: the booking panel, a translucent blurred ink panel with a 2 px lamp rule on top containing a real `<form>` (arrive date, leave date, guests select, room select, submit button) in a five-column grid on desktop, two columns from 640 px, one column on phones. A status line under the fields is `aria-live`.
3. **Harbour notices** is the proof band: a `<dl>` styled like a tide table. Five entries (high water, when the boat sails, breakfast, kitchen, quay lamps) in big serif times with small sans qualifiers, separated by vertical hairlines on desktop and stacked two-up with horizontal rules on phones. Sits on the raised surface with hairlines top and bottom.
4. **Rooms** is a ruled list, not cards: each row is a three-column grid (serif name with a muted count underneath, a 60-character description, a serif price on the right). Rows collapse to a single column on phones.
5. **The sail** (how it works) on the raised surface, two columns from 900 px. Left: an ordered log of the morning with real clock times in serif lamp colour against a thin vertical rule with small ring markers. Right: a bordered "Bring" aside with three bullets and a weather note.
6. **Rates and story**: left a rate card with a 3 px lamp rule on its left edge and a real `<table>` of winter rates plus fine print and a button; right a large serif blockquote from the owners with a short lamp dash before the attribution.
7. **Final call** is centred with a faint warm radial glow rising from the bottom edge and one lamp button.
8. **Footer**: address and contact on the left, the five links on the right.

## Motion
- The only moving thing on the page is the harbour canvas. Each frame: a sky gradient with a low warm glow from the town, a deterministic skyline of dark blocks and three masts, a quay edge, then the water. Seven lamps (six warm, one cold) sit a little above the horizon with a radial glow and a faint post. Below the horizon, 84 rows on phones or 124 rows on desktop are spaced wider as they come toward the viewer (`y = hz + (t² × 0.65 + t × 0.35) × span`). For every row and every lamp two rectangles are drawn in `lighter` composite mode: a wide faint one and a narrow bright one. Their horizontal offset is a sum of two sines of `y` and time (a fast ripple and a slow swell), their width breathes with a third sine, and a per-row flicker modulates alpha. Alpha falls off as `(1 - t)^1.4`, and the narrow sliver is skipped wherever a second, faster noise product drops below a threshold, so the streaks break up the way a reflection does on moving water. Every third row also gets a very faint pale sliver that drifts across the surface. The lamps themselves flicker by a few percent.
- Runs on `requestAnimationFrame`, device pixel ratio capped at 1.5, paused when the hero leaves the viewport or the tab is hidden, redrawn on resize after a 120 ms debounce.
- Buttons have a 200 ms background and transform transition. Nothing fades in on scroll.
- `prefers-reduced-motion: reduce`: the canvas draws one complete frame at a fixed time and never starts the loop; all CSS animations and transitions are off; smooth scrolling is off. Every piece of content is visible without motion.
- Booking form: JS sets the arrival minimum to today and the departure minimum to the day after arrival; submit is intercepted and the status line reports the dates and nights chosen. Replace that handler with your booking endpoint.

## Rebuild instructions
1. Create a single page with the `:root` tokens above and load Cormorant Garamond (500, 600, italic 500) and Alegreya Sans (400, 500, 700, italic 400) from Google Fonts. Set body to Alegreya Sans 17 px / 1.55 on the ink base with `font-variant-numeric: tabular-nums`.
2. Build the absolutely positioned header: brand link with an inline SVG lantern, a `<nav>` with four anchor links, and a ghost link to `#book`. Hide the link list under 760 px.
3. Build the hero as a `100svh` flex column with `isolation: isolate`, a `<canvas id="harbour">` absolutely filling it at `z-index: -1`, then the kicker, h1 and lede, then the booking panel.
4. Write the canvas script: read the RGB tokens from `:root`, size the canvas to its CSS box times a capped DPR, place the horizon 14 px below the bottom edge of the hero lede (measured from the DOM, clamped between 30 % and 80 % of the canvas height, re-measured once fonts are ready) so the water always shows in the gap between the copy and the booking panel, generate the skyline blocks from a tiny seeded LCG so it is stable across frames, then implement `draw(t)` as described under Motion. Wire `requestAnimationFrame`, `IntersectionObserver`, `visibilitychange` and a debounced resize. If `prefers-reduced-motion` matches, call `draw` once and stop.
5. Make the booking panel a real `<form method="get" action="#book">` with labelled `date`, `date`, `select`, `select` inputs and a submit `<button>`; add the `aria-live` status paragraph. Add the small date-minimum script and the demo submit handler, or point `action` at your backend.
6. Add the harbour notices `<dl>` on the raised surface with the five-column desktop layout and the two-column phone layout.
7. Add the rooms section as a `<ul>` of three-column rows with hairlines, then the sail section (ordered log with times plus the "Bring" aside), then the rates section (rate table card plus owners' blockquote), the final call, and the footer.
8. Add `:focus-visible` rings in the lamp colour, the reduced-motion block that disables all animation and transitions, and check the page at 375 px for horizontal overflow (the form grid must be one column there).
9. In React/Next or Astro, keep the canvas code in a `useEffect`/client script with the same start/stop logic, and keep every colour as a CSS custom property so the client can retheme from one place.
