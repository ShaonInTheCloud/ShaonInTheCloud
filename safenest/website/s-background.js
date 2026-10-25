/* SafeNest neon S grid: one bold S per cell, glowing and breathing, behind every page. */
(() => {
  const wrap = document.createElement('div');
  wrap.innerHTML = '<svg class="sbg" aria-hidden="true" xmlns="http://www.w3.org/2000/svg">\n  <defs>\n    <symbol id="sn-s" viewBox="-34 -34 190 210" overflow="visible">\n      <path class="s-glow" d="M118,18 H46 A30,30 0 0 0 46,78 H78 A30,30 0 0 1 78,138 H6"/>\n      <path class="s-a" d="M118,18 H46 A30,30 0 0 0 46,78 H78 A30,30 0 0 1 78,138 H6"/>\n      <path class="s-gap" d="M118,18 H46 A30,30 0 0 0 46,78 H78 A30,30 0 0 1 78,138 H6"/>\n      <path class="s-b" d="M118,18 H46 A30,30 0 0 0 46,78 H78 A30,30 0 0 1 78,138 H6"/>\n      <path class="s-gap2" d="M118,18 H46 A30,30 0 0 0 46,78 H78 A30,30 0 0 1 78,138 H6"/>\n      <path class="s-c" d="M118,18 H46 A30,30 0 0 0 46,78 H78 A30,30 0 0 1 78,138 H6"/>\n      <path class="s-flow" d="M118,18 H46 A30,30 0 0 0 46,78 H78 A30,30 0 0 1 78,138 H6"/>\n    </symbol>\n    <filter id="sn-blur" x="-40%" y="-40%" width="180%" height="180%"><feGaussianBlur stdDeviation="9"/></filter>\n  </defs>\n  <g id="sn-grid"></g>\n</svg>';
  const svg = wrap.firstElementChild;
  document.body.prepend(svg);
  const g = svg.querySelector('#sn-grid');
  function build() {
    const w = innerWidth, h = innerHeight;
    const CELL = Math.max(170, Math.min(260, Math.round(w / 6)));
    svg.setAttribute('viewBox', '0 0 ' + w + ' ' + h);
    const cols = Math.ceil(w / CELL) + 1, rows = Math.ceil(h / CELL) + 1, size = CELL * 0.62;
    let out = '';
    for (let r = 0; r < rows; r++) for (let c = 0; c < cols; c++) {
      const x = c * CELL + (CELL - size) / 2, y = r * CELL + (CELL - size) / 2, d = ((r * 3 + c * 5) % 7) * 0.55;
      out += '<use href="#sn-s" x="' + x + '" y="' + y + '" width="' + size + '" height="' + size * 1.1 + '" style="--d:' + d + 's"/>';
    }
    g.innerHTML = out;
  }
  build();
  let t; addEventListener('resize', () => { clearTimeout(t); t = setTimeout(build, 150); }, { passive: true });
})();
