# Aurora — AI / Tech
A deep indigo night with a single curtain of light drifting across the hero. The page sells Northlight, an invented on-device meeting-notes app for macOS, to privacy-minded professionals and small teams who are tired of cloud recorders. The mood is quiet and certain rather than hyped: one luminous element, a soft serif headline, and plain sentence-case copy with real numbers (1.9 s, 38 languages, €9 a month). Everything after the hero is ruled lists and calm two-tone type.

## Tokens
- Base `#0b1026`, base-2 `#101636`, surface `#141b40`
- Line `rgba(190,205,255,.14)`
- Text `#e9ecfb`, secondary text `#aab3d6` (both pass 4.5:1 on base)
- Accent mint `#7ef2c3` with ink `#06261b` for filled buttons; secondary accent violet `#b89bff`
- Aurora hues as numbers on `:root`: `--aurora-a: 150`, `--aurora-b: 200`, `--aurora-c: 275` (HSL hue, read by the canvas script)
- Display: Fraunces, variable (weight 300–700, optical size 9–144, SOFT axis). Chosen because its optical-size and SOFT axes let the same family be a crisp 144-opsz headline, a soft italic kicker and a light 300-weight statistic without a second display face. It reads as "considered", not "startup".
- Body: Instrument Sans 400–600
- Max width 1120 px, gutter `clamp(16px, 4vw, 48px)`, buttons are pills

## Layout
1. **Nav** — sticky, blurred, 64 px. Brand (glowing dot + "Northlight" in Fraunces) left, four links centre-right (hidden under 720 px), ghost pill "Download for Mac" right.
2. **Hero** — min-height 92vh, content aligned to the bottom of the viewport so the aurora owns the upper two-thirds. Two columns 1.3fr / 0.9fr: left has italic kicker, h1 with the word "leave" in italic mint, 34-ch lede, filled CTA plus a one-line "Free for 20 meetings a month" note. Right is a glass card showing a finished note (decisions with owners). Stacks to one column under 860 px.
3. **Proof band** — an ordered list of four figures in a ruled grid (no cards): "0 bytes", "1.9 s", "38", "Any call". Light 300-weight Fraunces numerals with 22-ch captions. 2-up under 860 px, 1-up under 480 px.
4. **How it works** — heading and a one-sentence explanation side by side, then three ruled columns labelled "first / then / finally" in italic violet (a real sequence). Each has an h3 (Quiet capture, Speaker threads, Action ledger), a 34-ch paragraph and a tiny fake-UI snippet in a bordered box.
5. **Privacy strip** — one large rounded box with a faint violet-to-mint gradient: heading "Unplug the Wi-Fi mid-meeting. Nothing changes." left, a definition list (Model, Hardware, Network use, Audit) right.
6. **Pricing** — three plans Personal €0 / Pro €9 / Team €14 per seat. The middle Pro plan is wider (1 / 1.15 / 1), on a lighter surface with a mint border; the others are flat and bordered. On phones Pro is listed first.
7. **Final CTA** — centred box with a radial mint/violet glow bleeding in from the top edge, h2, one sentence, filled + ghost buttons.
8. **Footer** — single ruled row: copyright left, four links right.

## Motion
- **Hero aurora (the one memorable moment).** A `<canvas>` fills the hero, blurred 14 px by CSS. Three bands (hues from the `--aurora-*` tokens) are drawn every frame as vertical gradient strips 5–7 px wide across the width. Each strip's top edge and length are a sum of three sines of x and time, so the curtain ripples and drifts. `globalCompositeOperation = 'lighter'` makes overlaps glow. Pointer position is smoothed (`p += (target - p) * 0.045`) and a Gaussian of horizontal distance lifts the curtain, lengthens it and brightens it near the cursor; vertical pointer position nudges the whole curtain up or down slightly. Pointer leaving the hero eases back to centre. The loop pauses when the hero is off-screen (IntersectionObserver) or the tab is hidden. DPR is capped at 1.5.
- Hero text rises 18 px and fades in over 0.9 s with `cubic-bezier(.2,.7,.2,1)`, staggered 0 / 0.12 / 0.22 s; the note card at 0.35 s. The brand dot's glow pulses on a 4 s loop.
- Nothing else animates. No scroll-triggered reveals on later sections.
- **Reduced motion:** the canvas draws one static frame (time preset so the curtain is mid-ripple) and never animates; it redraws on resize only. Text animations and the pulse are not applied, so all content is visible immediately. Smooth scrolling is off.

## Rebuild instructions
1. Create a single page with the two Google Fonts linked in `<head>`: Fraunces (ital, opsz 9..144, wght 300..700, SOFT 0..100) and Instrument Sans (400–600). Put every colour and the three aurora hues on `:root` as custom properties.
2. Build the sticky `<header>` containing a `<nav aria-label="Primary">` with the brand link, a `<ul>` of four anchor links and a ghost pill link. Hide the list under 720 px.
3. In `<main>`, make the hero a relative, overflow-hidden section with `min-height: min(92vh, 880px)`, `display: grid; align-items: end`. Insert a `<canvas aria-hidden="true">` absolutely positioned behind the content with `filter: blur(14px) saturate(1.15)`, and a `::before` overlay that fades from transparent to the base colour over the bottom 65% so text stays readable.
4. Write the aurora script (about 80 lines, vanilla JS, no library): size the canvas to its CSS box times a DPR capped at 1.5; keep smoothed pointer `px, py` and target `tx, ty`; per frame, clear, set `lighter` compositing, and for each of three band objects `{h, s, l, a, f, o, amp, base}` loop `i` from 0 to width in steps of 5 (7 under 600 px), compute `u = i / W`, `near = exp(-(u-px)^2 * 14)`, `wave` as the three-sine sum, `top = H * (base + amp*wave - near*.08 + (py-.45)*.12)`, `len = H * (.30 + .12*sin(...) + near*.16)`, build a vertical `createLinearGradient` from transparent to `hsla(h, s, l+near*16, a+near*.28)` to transparent, and `fillRect(i, top, step+1, len)`. Increment time and request the next frame.
5. Attach `pointermove` on the hero to update `tx, ty` as fractions of the hero box; `pointerleave` resets them to `.5, .45`. Observe the canvas with IntersectionObserver and listen to `visibilitychange` to stop and restart the loop. If `prefers-reduced-motion: reduce` matches, set `t = 140`, draw once, and never loop.
6. Hero content: a two-column grid (1.3fr / 0.9fr, single column under 860 px). Left: italic Fraunces kicker, the `<h1>` with one italic mint word, a 34-ch lede in secondary text, a flex row with the filled CTA and a small caption. Right: an `<aside>` glass card (blurred translucent background, 1 px line border, 18 px radius) with a meta row, a bold "Decisions" label, a mint pill tag and a three-item list.
7. Proof band: a `<section aria-label="Key figures">` holding an `<ol>` styled as a four-column ruled grid with a top border and left borders between items; big light Fraunces `<strong>` numbers and short `<span>` captions. Collapse to 2 and then 1 column.
8. How it works: a two-column head (h2 + paragraph), then three `<article class="step">` in a ruled three-column grid. Each step has an italic ordinal word, an h3, a paragraph and a bordered fake-UI box using plain divs (speaker rows, a gradient progress bar, checklist rows). Under 860 px, stack with top rules instead of left rules.
9. Privacy strip: one bordered box with a diagonal violet-to-mint gradient at 5–7% opacity, a kicker, an h2, a paragraph containing an inline `<code>` path, and a two-column `<dl>` on the right.
10. Pricing: three `<article class="plan">` in a `1fr 1.15fr 1fr` grid. The lead plan gets the surface background, a mint border and a soft mint drop shadow; others are flat with the line border. Prices in light Fraunces at 2.6 rem with a small body-font qualifier. Feature lists use a 6 px mint dot. Under 860 px stack and move the lead plan first with `order: -1`.
11. Final CTA: a centred bordered box on base-2 with a `::before` radial glow (mint and violet, 22–28% alpha) positioned above the box edge, an h2, a sentence and two pill buttons.
12. Footer: a ruled top border, a flex row with the copyright span and a `<ul>` of four links (one is the contact mailto).
13. Add `:focus-visible { outline: 2px solid var(--accent); outline-offset: 4px }`, `html { scroll-behavior: smooth }` with a reduced-motion override, and the `@media (prefers-reduced-motion: no-preference)` block that wraps the rise and pulse keyframes.
14. Check at 375 px: gutters are 16 px, the hero stacks, the note card and pricing plans are full width, and nothing overflows horizontally. Confirm there is exactly one `<h1>` and the landmarks are header, nav, main, footer.
