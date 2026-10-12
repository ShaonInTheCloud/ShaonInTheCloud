# Lumen Pages — template authoring spec

Every template is an ORIGINAL, self-contained cinematic landing page that a buyer can
drop into any stack. It ships as a folder:

```
templates/<slug>/
  index.html     the live demo (single file: inline <style> and <script>)
  prompt.md      the "rebuild it in your stack" prompt (see format below)
  meta.json      {"slug","name","category","tone","free":bool,"tagline","sections":[...],"fonts":[...]}
```

## Hard rules
- Single HTML file. No external JS. Fonts only from Google Fonts (`<link>`), max 2 families.
- No images or video files. Visuals come from CSS, SVG, or a `<canvas>` you draw yourself.
- No frameworks, no build step. Vanilla CSS + JS. Under 60 KB.
- Original everything: copy, brand name (invent one that fits the industry), layout, motion.
  Do not reproduce any existing site, product, brand, or template. Avoid generic AI-site tells:
  tracked-out ALL-CAPS eyebrow labels, "01 / 02 / 03" markers unless it is a real sequence,
  identical rounded cards with the same shadow, a single acid-green accent on black,
  "→" glued to every link, fade-up-on-every-section.
- Dark tone templates: base is a deep tinted dark (not #000/#111), light comes from one
  animated element. Light tone templates: real paper/plaster/linen warmth, not pure white.
- One memorable moment per page (hero), everything else disciplined.
- Motion respects `prefers-reduced-motion` (animations off, content fully visible).
- Works at 375 px wide with no horizontal scroll; 16 px side gutters minimum on phones.
- Visible keyboard focus; text contrast at least 4.5:1 for body, 3:1 for display.
- Semantic HTML: one `<h1>`, landmarks (`header`, `main`, `nav`, `footer`), buttons are `<button>`
  or `<a href>`.
- Real copy for the invented brand: specific, plain, sentence case. No lorem ipsum.
- All colors as CSS custom properties on `:root` so a buyer can retheme in one place.

## Required sections (in this order, adapt names to the industry)
1. Nav (brand + 3–4 links + one action)
2. Hero with the memorable animated element and ONE primary call to action
3. A proof/feature band that uses the industry's own vernacular (numbers, a process, a gallery…)
4. A "how it works" or product detail section
5. Pricing or offer (if it fits) or testimonial/story
6. Final call to action
7. Footer

## prompt.md format
```
# <Name> — <category>
One-paragraph description of the page's mood and who it is for.

## Tokens
- Base, surface, text, accent values (hex), fonts (names + weights)

## Layout
Section-by-section description with alignment and spacing intent.

## Motion
Exactly what moves, what triggers it, durations/easings, reduced-motion behavior.

## Rebuild instructions
Numbered steps a developer or an AI coding tool follows to recreate it in React/Next, Astro, or plain HTML.
```

## Quality bar
Before finishing, open your own HTML mentally at phone width and desktop; check the hero is the
single bold thing, the rest is calm, and nothing reads as a default.
