// Lumen Pages hero: a WebGL light field. Three soft lights (a warm beam, an
// ultramarine wash, a faint rose) move through layered noise and lean toward
// the pointer. No library. Reduced motion renders one still frame.
(() => {
  const canvas = document.querySelector('.hero canvas');
  if (!canvas) return;
  const gl = canvas.getContext('webgl', { antialias: false, alpha: false, powerPreference: 'low-power' });
  if (!gl) { canvas.remove(); return; }
  const reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;

  const vert = `attribute vec2 p; void main(){ gl_Position = vec4(p, 0.0, 1.0); }`;
  const frag = `
precision highp float;
uniform vec2 u_res; uniform float u_t; uniform vec2 u_m; uniform float u_on;
vec3 hash3(vec2 p){ vec3 q = vec3(dot(p,vec2(127.1,311.7)), dot(p,vec2(269.5,183.3)), dot(p,vec2(419.2,371.9))); return fract(sin(q)*43758.5453); }
float noise(vec2 p){ vec2 i = floor(p), f = fract(p); f = f*f*(3.0-2.0*f);
  float a = hash3(i).x, b = hash3(i+vec2(1,0)).x, c = hash3(i+vec2(0,1)).x, d = hash3(i+vec2(1,1)).x;
  return mix(mix(a,b,f.x), mix(c,d,f.x), f.y); }
float fbm(vec2 p){ float v = 0.0, a = 0.5; for(int i=0;i<5;i++){ v += a*noise(p); p = p*2.03 + 7.1; a *= 0.5; } return v; }
float beam(vec2 uv, vec2 c, float r){ float d = length(uv - c); return exp(-d*d/(r*r)); }
void main(){
  vec2 uv = gl_FragCoord.xy / u_res; uv.x *= u_res.x / u_res.y;
  float t = u_t * 0.08;
  vec2 warp = vec2(fbm(uv*1.6 + t), fbm(uv*1.6 - t*0.7)) - 0.5;
  vec2 q = uv + warp * 0.35;
  vec2 m = u_m; m.x *= u_res.x / u_res.y;
  vec2 cA = vec2(0.72 + 0.12*sin(t*1.3), 0.62 + 0.08*cos(t*0.9)) + (m - vec2(0.6,0.5)) * 0.18;
  vec2 cB = vec2(0.25 + 0.10*cos(t*0.8), 0.35 + 0.10*sin(t*1.1)) + (m - vec2(0.6,0.5)) * -0.10;
  vec2 cC = vec2(0.55 + 0.20*sin(t*0.5), 0.15 + 0.06*sin(t*1.7));
  vec3 beamCol = vec3(1.0, 0.86, 0.60);
  vec3 ultra  = vec3(0.357, 0.424, 1.0);
  vec3 rose   = vec3(1.0, 0.56, 0.69);
  vec3 col = vec3(0.043, 0.047, 0.078);
  float grain = fbm(q*3.0 + t*0.3);
  col += beamCol * beam(q, cA, 0.46) * (0.75 + 0.55*grain) * u_on;
  col += ultra  * beam(q, cB, 0.6) * 0.95 * (0.7 + 0.5*grain) * u_on;
  col += rose   * beam(q, cC, 0.38) * 0.32 * u_on;
  // dust: a faint, fine noise so the gradient never bands
  col += (hash3(gl_FragCoord.xy).x - 0.5) * 0.012;
  gl_FragColor = vec4(col, 1.0);
}`;
  const sh = (type, src) => { const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s); return s; };
  const prog = gl.createProgram();
  gl.attachShader(prog, sh(gl.VERTEX_SHADER, vert));
  gl.attachShader(prog, sh(gl.FRAGMENT_SHADER, frag));
  gl.linkProgram(prog);
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) { canvas.remove(); return; }
  gl.useProgram(prog);
  const buf = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, buf);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1,-1, 1,-1, -1,1, 1,1]), gl.STATIC_DRAW);
  const loc = gl.getAttribLocation(prog, 'p');
  gl.enableVertexAttribArray(loc);
  gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
  const u = n => gl.getUniformLocation(prog, n);
  const uRes = u('u_res'), uT = u('u_t'), uM = u('u_m'), uOn = u('u_on');

  let w = 0, h = 0;
  function resize() {
    const dpr = Math.min(devicePixelRatio || 1, 1.5);
    const r = canvas.getBoundingClientRect();
    w = Math.max(1, Math.round(r.width * dpr)); h = Math.max(1, Math.round(r.height * dpr));
    if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; gl.viewport(0, 0, w, h); }
  }
  const mouse = { x: 0.6, y: 0.5, tx: 0.6, ty: 0.5 };
  const hero = canvas.parentElement;
  hero.addEventListener('pointermove', e => { const r = hero.getBoundingClientRect(); mouse.tx = (e.clientX - r.left) / r.width; mouse.ty = 1 - (e.clientY - r.top) / r.height; }, { passive: true });
  hero.addEventListener('pointerleave', () => { mouse.tx = 0.6; mouse.ty = 0.5; });

  const start = performance.now();
  let on = reduced ? 1 : 0, visible = true, raf = 0;
  function frame(now) {
    resize();
    const t = (now - start) / 1000;
    if (!reduced) on = Math.min(1, on + 0.016); // the light switches on over the first second
    mouse.x += (mouse.tx - mouse.x) * 0.06; mouse.y += (mouse.ty - mouse.y) * 0.06;
    gl.uniform2f(uRes, w, h); gl.uniform1f(uT, reduced ? 12.0 : t); gl.uniform2f(uM, mouse.x, mouse.y); gl.uniform1f(uOn, on);
    gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    if (!reduced && visible) raf = requestAnimationFrame(frame);
  }
  new IntersectionObserver(([e]) => { visible = e.isIntersecting; if (visible && !reduced && !raf) raf = requestAnimationFrame(frame); if (!visible) { cancelAnimationFrame(raf); raf = 0; } }).observe(hero);
  document.addEventListener('visibilitychange', () => { if (document.hidden) { cancelAnimationFrame(raf); raf = 0; } else if (visible && !reduced && !raf) raf = requestAnimationFrame(frame); });
  raf = requestAnimationFrame(frame);
})();

// Catalogue filters (progressive: everything is visible without JS).
(() => {
  const bar = document.querySelector('.filters');
  if (!bar) return;
  const tiles = [...document.querySelectorAll('.grid .tile')];
  bar.addEventListener('click', e => {
    const b = e.target.closest('button'); if (!b) return;
    bar.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', x === b));
    const f = b.dataset.filter;
    tiles.forEach(t => { t.hidden = !(f === 'all' || t.dataset.category === f || (f === 'free' && t.dataset.free === 'true')); });
  });
})();

// Copy buttons for prompts and snippets.
document.querySelectorAll('[data-copy]').forEach(btn => {
  btn.addEventListener('click', async () => {
    const src = document.querySelector(btn.dataset.copy); if (!src) return;
    try { await navigator.clipboard.writeText(src.textContent); btn.textContent = 'Copied'; setTimeout(() => { btn.textContent = btn.dataset.label || 'Copy'; }, 1600); }
    catch { btn.textContent = 'Select and copy manually'; }
  });
});
