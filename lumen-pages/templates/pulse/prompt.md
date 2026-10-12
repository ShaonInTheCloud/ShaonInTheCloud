# Pulse — Health / Science
A dark, clinical landing page for a measured product: a wearable ECG patch, a sleep-lab sensor, a lab instrument. The ground is a deep teal-black and the single source of light is a mint ECG trace that draws itself across the hero and then keeps a calm 56-beats-a-minute rhythm, with a field of soft dots breathing behind it. Everything below is restrained and evidence-led: study figures with their caveats, a step list, a mock report excerpt, a full specifications table in tabular figures, and an evaluation offer. It is written for clinicians and procurement, not consumers, so the copy is specific and never breathless.

## Tokens
- Base `#061417`; surface `#0a1d21`; raised surface `#0f262b`
- Hairline `rgba(160,205,205,.14)`
- Text `#e2eeef`; muted text `#9db5b7`
- Trace accent mint `#6fe3cf` (hover `#8af0de`), deeper mint `#2fb9a3` for rules and markers; text on mint `#04201b`
- Secondary accent amber `#f0c27a`, used only for flagged episodes in the report excerpt
- `--mint-rgb 111,227,207` for the canvas; `--beat 1.07s` is the heartbeat period shared by the trace and the readout dot
- Display and body: Manrope 400, 500, 600, 700, 800. A clean geometric sans with open counters; 800 for the h1, 700 for headings, 600 for nav and labels.
- Figures: IBM Plex Mono 400 and 500 for every number that has to line up (study figures, readout, spec values, kit quantities, prices) and for the small kicker. `font-variant-numeric: tabular-nums` is set on `body` as well.
- Body size 17 px, line height 1.55.

## Layout
Content width `min(1140px, 100% - 2 × gutter)`, gutter `clamp(16px, 4vw, 40px)`.

1. **Nav** is absolutely positioned over the hero: brand with a small trace glyph, four links (hidden under 760 px and repeated in the footer), a ghost "Request a kit" link.
2. **Hero** is a `100svh` grid of two rows. Row one, vertically centred: a mono kicker, the h1 at `clamp(2.4rem, 5.6vw, 4.4rem)` with the second clause in mint, a muted lede of about 58 characters, then one mint button and a quiet underlined secondary link. Row two is the monitor band: a 190 px-tall SVG with a faint baseline and the trace, masked so it fades out on the left, with a mono readout pill in the top-right corner (pulsing dot, bpm figure, "sinus rhythm · lead II"). The particle canvas fills the whole hero behind both rows.
3. **Study figures** is the proof band: a heading, then four figures in large mono numerals with small unit suffixes, each with a 2 px mint-deep rule on its left and a one-line caveat underneath. Two-up on phones, four-up from 860 px. A fine-print paragraph states the sample size and that the numbers come from the primary analysis.
4. **How it works**: two columns from 920 px. Left: an ordered list of three steps whose markers are the real days ("Day 1", "Day 1–14", "Day 15") in mono mint. Right: a mock report card in mono: patient and burden header, an SVG strip with an amber-tinted flagged region and label, then three episode rows with amber or mint dots.
5. **Specifications** on the surface colour: a sticky heading column on the left, and a real `<table>` with a caption, row headers in muted sans and values in mono, split into three `<tbody>` groups (physical, signal, connectivity) separated by a heavier rule.
6. **Evaluation kit and quote**: left a kit card with a soft diagonal surface gradient, a ruled list of contents with mono quantities, a mono price with a sans qualifier, pricing after evaluation, and a mailto button. Right a large medium-weight blockquote from a clinician.
7. **Final call** is centred over a faint mint radial glow rising from the bottom, with one button.
8. **Footer**: address and a regulatory note on the left, four links on the right.

## Motion
- **Trace draw-in.** On load the script builds the path: `M0 120` followed by one PQRST complex (`h70 c8-16 20-16 28 0 h22 l8 8 10-86 10 110 6-32 h26 c12-26 34-26 46 0 h74`, exactly 300 user units wide) repeated five times, so the path spans 1500 units in a 1200-unit viewBox. `stroke-dasharray` and `stroke-dashoffset` are set to the total length and a 3 s `cubic-bezier(.45,0,.2,1)` animation brings the offset to zero.
- **Live rhythm.** On `animationend` the dash styles are removed and the monitor gets a `live` class: the `<g>` around the path animates `translateX(-300px)` over `--beat` (1.07 s) linearly and infinitely. Because the path is periodic at 300 units, each loop joins seamlessly and the trace appears to scroll left for ever at 56 bpm. The readout dot scales from 1.5 to 1 on the same period, and a 4.2 s interval nudges the bpm figure by one within 54–59.
- **Particle field.** A canvas behind the hero holds between 36 and 110 dots (area / 14000). Each dot is a pre-rendered 64 px radial sprite drawn with `drawImage`; its radius and alpha breathe on a per-dot sine (`0.5 + 0.5 sin(t × speed + phase)`), and it drifts slowly upward and sideways, wrapping at the edges. DPR capped at 1.5, paused off-screen and when the tab is hidden, reseeded on resize after a 120 ms debounce.
- Buttons have a 200 ms background and transform transition. Nothing fades in on scroll.
- `prefers-reduced-motion: reduce`: the trace is set to its full path with no dash animation and the `live` class is added without the roll keyframes running (all animations are disabled by a global rule); the canvas draws one frame at a fixed time and never loops; the bpm figure stays at 56.

## Rebuild instructions
1. Create a single page with the `:root` tokens above; load Manrope (400–800) and IBM Plex Mono (400, 500) from Google Fonts. Body is Manrope 17 px / 1.55 on the base colour with tabular numerals.
2. Build the absolutely positioned header: brand link with an inline SVG trace glyph, a `<nav>` of four anchors, and a ghost link to `#kit`.
3. Build the hero as a `100svh` grid (`1fr auto`) with `isolation: isolate`. Put a `<canvas id="field">` absolutely inside it at `z-index: -1`. Row one holds kicker, h1, lede and the CTA row. Row two is `.monitor` with hairlines top and bottom, an SVG `viewBox="0 0 1200 200" preserveAspectRatio="xMidYMid slice"` containing the baseline `<line>` and `<g class="roll"><path class="trace" id="trace"></path></g>`, plus the absolutely positioned readout pill.
4. Add the trace script: build the five-period path string, set it as `d`; if reduced motion is on, add `live` and stop; otherwise set dasharray/offset from `getTotalLength()`, add the `drawing` class, and on `animationend` clear the dash styles and add `live`. Define `@keyframes draw` and `@keyframes roll`, and apply `roll` only under `.live .roll`.
5. Add the particle script: pre-render the sprite on an offscreen canvas, seed dots with a tiny LCG so the field is stable, implement `draw(t)` with the breathing and drift above, and wire `requestAnimationFrame`, `IntersectionObserver`, `visibilitychange` and a debounced resize. Draw a single frame when reduced motion is on.
6. Add the study-figures band, the how-it-works section (step list plus mock report with its small inline SVG), the specifications `<table>` with `scope="row"` headers and three `<tbody>` groups, the evaluation-kit card beside the clinician quote, the final call and the footer.
7. Add `:focus-visible` rings in mint, the reduced-motion block that disables all animations and transitions, and verify at 375 px that the readout pill hides its long label and the spec table wraps without horizontal scroll.
8. In React/Next or Astro, run both scripts from a client-side effect with the same start/stop logic, keep `--beat` as the single timing token, and keep every colour as a CSS custom property so the client can retheme from one place.
