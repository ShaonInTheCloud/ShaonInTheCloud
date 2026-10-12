# Forge — Developer tools
A graphite landing page for a CLI that rehearses database migrations on a local snapshot before shipping them. The mood is a warm amber phosphor terminal in a dark room: calm, exact, slightly nostalgic without being retro. It is for backend and platform engineers who read terminal output faster than marketing copy, so the hero is a terminal session that types itself and whose result lines fly out to become the feature list beside it.

## Tokens
- Base `#1a1917` (warm graphite), raised surface `#201f1c`, terminal well `#121211`
- Rules `#2f2d28` and `#403d36`
- Text `#ece7dd`, secondary `#aaa497`, tertiary `#7b7668`
- Accent (phosphor) `#ffb15c`, phosphor light `#ffd59b`, phosphor wash `rgba(255,177,92,.14)`
- Primary button text on amber `#1a1200`
- Fonts: Alegreya Sans 400/500/700 + italic 400 for everything written; JetBrains Mono 400/600 for the terminal, code, figures and buttons that contain commands
- Container 1120 px, side gutter `clamp(16px, 4vw, 48px)`

## Layout
1. Sticky nav, 60 px, blurred graphite. Brand is a small amber-outlined square with a filled centre plus the wordmark. Four text links (Install, How it works, Pricing, Changelog) hidden under 800 px. The action on the right is a ghost button whose label is the command `forge init` in mono.
2. Hero, left-aligned. H1 at `clamp(2.1rem, 5.6vw, 3.9rem)` with the word "before" in italic light amber. Lede at 1.15 rem in secondary text, max 52 ch. Two actions: a filled amber button and an underlined text link. Below, a two-column stage (1.3fr / 1fr above 900 px, stacked below): a terminal window on the left (title bar with three dots, the first one lit amber, a "replay" button on the right; body 21–23 rem tall, scrolls internally) and on the right four feature slots separated by hairlines. Each slot starts as a dim mono line "waiting for rehearsal"; its heading and paragraph are invisible until the matching terminal line lands on it.
3. Proof band on the raised surface, four figures in mono amber (0.3 s, 9 s, 6, 1,900) with a one-sentence explanation under each. Two-by-two on phones with hairline dividers, four across on desktop.
4. Install: heading and a short paragraph side by side, then five rows (label, command, copy button). Commands are in the terminal well colour with the first word in amber. The copy button turns solid amber and reads "copied" for 1.6 s.
5. How it works: three numbered steps (a real sequence: Snapshot, Rehearse, Gate and ship) in a three-column ruled list: number, text, and a mono report block showing the command and three result lines.
6. Pricing: three plans as ruled rows, not cards. Columns on desktop: name, price in mono, three dash-prefixed lines, action. The Team plan is marked by a glowing amber bar on its left edge and gets the filled button.
7. Final call to action, centred, with a soft radial amber wash rising from the bottom. Heading, one sentence, filled button, and a mono release line.
8. Footer: company line, five links, and a one-line sign-off aligned right.

## Motion
- Terminal: starts 500 ms after load. Commands type character by character at 16–42 ms per character with a blinking block cursor (1 s steps). Output lines appear whole with 140 ms between them; result lines (prefixed by an amber check) get an extra 220 ms. One line animates three dots for 1.1 s to represent the rehearsal running.
- Flight: 260 ms after a result line that maps to a feature slot appears, a fixed-position clone of it is created at the line's rectangle and animated with the Web Animations API to the slot heading's rectangle over 620 ms, `cubic-bezier(.2,.7,.15,1)`, fading to 10 %. On finish the clone is removed, the slot's label changes to "proved in this session" in amber, and the heading and paragraph fade and rise in over 400–500 ms. A small amber dot with a glow marks landed slots on desktop.
- The terminal's amber glow is the single light source on the page (box-shadow plus a radial wash at the top of the window).
- Replay button clears and restarts the session.
- Copy buttons: `navigator.clipboard.writeText` in secure contexts with a hidden textarea and `execCommand('copy')` fallback.
- Reduced motion: the whole session is rendered immediately as static text, no cursor, no flights; all four feature slots are shown complete with their amber labels.

## Rebuild instructions
1. Create one HTML file with the Google Fonts link for Alegreya Sans (400, 500, 700, italic 400) and JetBrains Mono (400, 600). Put every colour above on `:root` as custom properties.
2. Build the sticky header: brand link, a `<nav>` with four anchors, and a ghost `<a>` whose text is a `<code>` element. Hide the nav links under 800 px.
3. Build the hero: `<h1>` with an `<em>` for the amber word, a lede, a filled anchor and a text anchor, then a `.stage` grid. Inside, a terminal block (`.term-bar` with three dots, a title and a replay `<button>`, then an empty `.term-body` that is `white-space: pre-wrap; overflow-wrap: anywhere; overflow-y: auto`) and a `.feats` list of four `.feat` blocks, each with a `.slot` span, an `<h3>` and a `<p>`.
4. Write the session as an array of `{t, s, f, d}` objects: `t` is `cmd`, `out`, `ok` or `spin`; `s` the text; `f` the feature index the line should fly to; `d` the pause after the line. Render `cmd` lines by inserting characters before a cursor span on a timer, `out` and `ok` lines at once, and `spin` with a CSS `steps(4)` dots animation.
5. Implement `fly(lineEl, index)`: read both rectangles with `getBoundingClientRect`, append a fixed `.ghost` div with the line's text at the start rectangle, `animate()` a translate to the heading's rectangle, and on finish remove the ghost, add `.on` to the feature and change the slot text.
6. Check `matchMedia('(prefers-reduced-motion: reduce)')` on load: when true, print every line immediately and mark every feature `.on`; otherwise call `play()`. Wire the replay button to the same choice.
7. Add the proof band as a four-cell grid with hairline borders (two columns under 800 px).
8. Add the install section: five `.row` grids with a `<small>` label, a `<code>` command and a `<button class="copy" data-copy="...">`. Attach the clipboard handler with the fallback described above and swap the label for 1.6 s.
9. Add the three-step list, each step a grid of number, text and `<pre>` report. Then the three pricing rows, marking the lead plan with a `::before` amber bar.
10. Add the final section with a `::before` radial wash, the footer with company line, links and sign-off.
11. Add the reduced-motion block: kill the cursor and dots animation, show feature text at full opacity, remove transitions.
12. Test at 375 px (no horizontal scroll, 16 px gutters), confirm one `<h1>`, visible focus rings on every control, and that the terminal's long commands wrap instead of overflowing.
