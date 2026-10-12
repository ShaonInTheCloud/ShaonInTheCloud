# Orbit — E-commerce
A single-product launch page for Halo, a ring lamp, set on black plum. The only light on the page comes from the lamp itself, which is drawn in layered SVG, turns a few degrees with the pointer, and recolours live when the buyer picks one of three finishes. It is for a small hardware brand taking pre-orders: one product, one price, a real buy form in the hero, a sticky add-to-cart bar once the form scrolls away, and calm ruled sections for specs, finishes and delivery terms.

## Tokens
- Base `#1a1020` (black plum), raised `#231527`, deeper raised `#2c1b31`
- Rules `#3a2740` and `#4d365a`
- Text `#f4ebf2`, secondary `#c3adc3`, tertiary `#8f7a92`
- Light (accent) `#ffd9a6`, warmer light `#ffb870`, light wash `rgba(255,217,166,.16)`
- Finish sets, each with body / highlight / shadow / trim:
  - Aubergine `#43204a` / `#7d4886` / `#1f0c23` / `#d2a86a` (default on `:root`)
  - Brushed brass `#a67c3c` / `#e9c882` / `#5a3d1c` / `#f7e8c6`
  - Bone `#e6dccb` / `#fbf7ee` / `#a4967f` / `#8b7f72`
- Fonts: Syne 500/700/800 for headings, figures and the wordmark; Archivo variable with the width axis (`wdth 75–125`, `wght 400–600`) for body, with `font-stretch: 78–92%` for labels, nav and small print
- Container 1160 px, side gutter `clamp(16px, 4vw, 48px)`

## Layout
1. Sticky nav, 62 px, blurred plum. Wordmark "ORBIT" in Syne 800 with a small glowing ring glyph. Four links (The lamp, Finishes, Delivery, Why we made it) hidden under 820 px, and a pill ghost action "Pre-order" linking to the form.
2. Hero, two equal columns above 900 px (copy left, lamp right); stacked with the lamp first on phones. H1 in Syne 800 at `clamp(2rem, 4.6vw, 3.3rem)`, lede in secondary text, a condensed shipping line. Then the buy form: a fieldset of three finish radios styled as pills with a gradient swatch, a quantity number input (1–4), a full-width filled pill button "Add to cart €289" whose total updates with quantity, a small-print line, and an `<output>` for the confirmation.
3. The lamp: a 400×520 figure with `transform-style: preserve-3d` inside a `perspective: 1100px` scene. Three stacked layers: a radial glow div at `translateZ(-70px)`, an SVG with base, dial and stem at `translateZ(0)`, and an SVG with the ring at `translateZ(50px)`. All fills use the finish custom properties through `style="fill:var(--fin-body)"` and gradient stops with `style="stop-color:var(--fin-hi)"`. A thin light-coloured inner circle and an inner radial gradient give the lit edge; a light pool ellipse sits on the base.
4. Proof band on the raised surface: four Syne figures in light amber (1,100 lm, 2,140, 30 days, 5 years) with one sentence each. Two by two on phones, four across on desktop.
5. "Three parts. No screws, no bulb." Exploded SVG diagram on the left (ring, stem, base separated along a dashed axis, labelled), a ruled spec list on the right with a Syne label column (Ring, Stem, Base, Power), a bold line and a sentence each.
6. Finishes: three large buttons, each a gradient chip with a small ring motif over the finish colour, a name, a sentence, and a "Choose …" line. The current one is outlined in light and prefixed "Selected ·". Clicking sets the matching radio in the hero form.
7. Delivery: a definition list (Ships, Shipping, Returns, Warranty, In the box) beside a founder quote in Syne 500 with a light left rule.
8. Final call to action, centred, with a radial light wash behind the heading. Heading, one sentence, a filled pill linking back to the form.
9. Sticky bar (outside `<main>`): finish dot, "Halo · Finish", price and ship date, and a filled "Add to cart" `<button type="submit" form="buy">`. Hidden by default.
10. Footer: company line, five links, and a one-line origin note aligned right.

## Motion
- Light: the glow layer and the lit inner edge breathe between 78 % and 100 % opacity over 4.5 s, ease-in-out, looping. This is the single animated light source.
- Tilt (only when `(hover:hover) and (pointer:fine)` and motion is allowed): `pointermove` on the hero maps the cursor's offset from the scene centre to `rotateY ±10°` and `rotateX ±7°`; a `requestAnimationFrame` loop eases current toward target at 12 % per frame; `pointerleave` returns to zero. Because the layers sit at different Z depths the ring parallaxes against the base.
- Finish switch: changing a radio or clicking a finish card sets `data-finish` on the lamp figure, the sticky bar dot and the exploded diagram; the attribute selectors swap the four finish custom properties so every fill, gradient and swatch recolours instantly. No per-element JS.
- Sticky bar: an `IntersectionObserver` on the form adds `.show` when the form has scrolled above the viewport (`boundingClientRect.bottom < 0`), sliding the bar up over 350 ms; the body gets 72 px bottom padding and the bar's button becomes focusable. It hides again when the form is back in view.
- Form submit is intercepted, the quantity is clamped to 1–4, and the `<output>` announces "Added n × Halo in Finish" with a 300 ms fade.
- Reduced motion: no breathing, no tilt (transform forced to none), no bar slide, no transitions; the lamp renders as a complete static frame in the chosen finish.

## Rebuild instructions
1. One HTML file. Link Syne (500, 700, 800) and Archivo with `wdth,wght@75..125,400..600`. Put the base palette and the Aubergine finish set on `:root`; add `[data-finish="brass"]` and `[data-finish="bone"]` rules that override only the five finish properties.
2. Header with brand, `<nav>` and the pill action. Hero `.wrap` as a grid; give the copy column `order: -1` above 900 px so the lamp leads on phones.
3. Draw the lamp: a `.scene` div with perspective containing a `<figure class="pdt" data-finish="aubergine">` with three absolutely positioned layers (glow div, body SVG, ring SVG) sharing the 400×520 viewBox. Use inline `style` for fills and gradient stops so custom properties apply. Add the breathe keyframes to the glow and the lit circles.
4. Write the buy `<form id="buy">`: three radios named `finish` with `data-name`, visually hidden, each followed by a `<label>` that carries a gradient swatch; a number input; a submit button containing a `<span id="total">`; a note; an `<output id="msg">`.
5. Write `sync()`: read the checked radio, set `data-finish` on the figure, bar dot and diagram, toggle `.cur` on finish cards, clamp the quantity, update the total and the bar title. Call it on radio change, quantity input, card click and load.
6. Intercept `submit`, call `sync()`, write the confirmation into the output.
7. Add the tilt: guard with `matchMedia` for fine pointer and reduced motion, track target angles from `pointermove`, ease in a rAF loop, reset on `pointerleave`.
8. Add the sticky `.bar` after `<main>` with `aria-hidden="true"` and `tabindex="-1"` on its button; observe the form with `IntersectionObserver` and toggle `.show`, `aria-hidden`, the button's tabindex and `body.barred`.
9. Build the proof band, the parts section (exploded SVG plus ruled spec list), the three finish buttons with `--c-hi/--c-body/--c-lo` set inline, the delivery definition list with the quote, the final call to action and the footer.
10. Add the reduced-motion block that removes the animations and transitions and forces the figure transform to none.
11. Test at 375 px for horizontal overflow, confirm one `<h1>`, that every control has a visible focus ring (including the hidden radios via `:focus-visible + label`), and that switching finish recolours the hero lamp, the diagram, the finish cards and the sticky bar together.
