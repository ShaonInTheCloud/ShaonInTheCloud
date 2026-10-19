/* SafeNest rabbit guide: a layered puppet of the brand rabbit that lives in the
   corner, hops when a new section arrives, runs to a navigation link when it is
   tapped, and speaks the heading of the section in view. Content is untouched;
   the rabbit only reads headings that already exist on the page. */
(() => {
  if (document.getElementById('sn-bun')) return;
  const RM = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const BW = 380, BH = 595, clamp = (v, a, b) => Math.max(a, Math.min(b, v));
  const bun = document.createElement('div');
  bun.id = 'sn-bun'; bun.setAttribute('aria-hidden', 'true');
  bun.innerHTML = '<div class="b-shadow"></div><div class="b-tilt"><i class="p base"></i><i class="p earL"></i><i class="p earR"></i><i class="p armL"></i><i class="p armR"></i></div>';
  const say = document.createElement('div');
  say.id = 'sn-say'; say.setAttribute('aria-hidden', 'true');
  document.body.append(bun, say);
  const tilt = bun.querySelector('.b-tilt'), shadow = bun.querySelector('.b-shadow');
  const el = n => bun.querySelector('.' + n);
  const eL = el('earL'), eR = el('earR'), aL = el('armL'), aR = el('armR');
  const mobile = () => innerWidth < 760;
  const S = { x: 0, y: 0, s: .4, vx: 0, vy: 0, vs: 0, f: 1, ph: 0, t: 0, wt: 0, jump: -1, rx: 0, ry: 0, e: [0, 0], ev: [0, 0], al: 0, ar: 0, calm: 0, init: false };
  let override = null, lastSay = '', sayTO = 0, mode = 'idle', curHead = null;
  const mouse = { x: 0, y: 0 };
  addEventListener('pointermove', e => { mouse.x = e.clientX / innerWidth * 2 - 1; mouse.y = e.clientY / innerHeight * 2 - 1; }, { passive: true });
  const MODES = { idle: { off: [4, -4], amp: 3, fr: 1 }, cheer: { off: [10, -10], amp: 20, fr: 3.2 }, wave: { off: [6, -18], amp: 3, fr: 2, waveR: 26 }, push: { off: [34, -34], amp: 6, fr: 5 } };

  // Headings the rabbit can talk about: the language the site is showing.
  function headingText(h) {
    const bn = document.documentElement.lang === 'bn' || document.body.classList.contains('lang-bn');
    const span = h.querySelector(bn ? '.translation-bn' : '.translation-en') || h.querySelector('.translation-en');
    return (span ? span.textContent : h.textContent).trim().replace(/\s+/g, ' ').slice(0, 70);
  }
  function headings() { return [...document.querySelectorAll('main h1, main h2, .hero h1, .page-intro h1, .product-intro h1')]; }

  function homeTarget() {
    const vw = innerWidth, vh = innerHeight;
    const h = mobile() ? 120 : Math.min(230, Math.max(150, vh * .3));
    const w = h * BW / BH;
    const right = Math.max(12, parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--sn-inset-right')) || 12);
    return { x: vw - w / 2 - right, y: vh - (mobile() ? 10 : 14), s: h / BH, mode };
  }
  function target() { return override || homeTarget(); }

  function showSay(text) {
    if (!text || text === lastSay) return;
    lastSay = text; say.textContent = text;
    const w = Math.min(240, innerWidth - 24);
    const left = clamp(S.x, w / 2 + 12, innerWidth - w / 2 - 12);
    say.style.left = left + 'px';
    say.style.top = Math.max(70 + say.offsetHeight, S.y - BH * S.s * .98) + 'px';
    say.style.setProperty('--tx', clamp(S.x - left, -w / 2 + 22, w / 2 - 22) + 'px');
    say.classList.add('show');
    clearTimeout(sayTO); sayTO = setTimeout(() => say.classList.remove('show'), 4200);
  }

  // Which heading is in view decides what the rabbit says and when it hops.
  let pendingSay = '';
  function watchHeadings() {
    const hs = headings(); if (!hs.length) return;
    const mid = innerHeight * .45; let best = null, bd = 1e9;
    for (const h of hs) { const r = h.getBoundingClientRect(); if (r.bottom < 0 || r.top > innerHeight) continue; const d = Math.abs(r.top + r.height / 2 - mid); if (d < bd) { bd = d; best = h; } }
    if (best && best !== curHead) {
      curHead = best; pendingSay = headingText(best);
      if (!RM) { S.jump = S.jump < 0 ? 0 : S.jump; mode = Math.random() < .5 ? 'wave' : 'cheer'; setTimeout(() => { if (!override) mode = 'idle'; }, 2600); }
    }
  }
  let scrollT; addEventListener('scroll', () => { clearTimeout(scrollT); scrollT = setTimeout(watchHeadings, 120); }, { passive: true });
  setTimeout(watchHeadings, 900);

  let last = performance.now();
  function tick(now) {
    requestAnimationFrame(tick);
    if (document.hidden) { last = now; return; }
    const dt = Math.min(.05, (now - last) / 1000); last = now;
    const T = target();
    if (!S.init) { S.x = T.x; S.y = RM ? T.y : -40; S.s = T.s; S.init = true; bun.classList.add('on'); }
    if (RM) { S.x = T.x; S.y = T.y; S.s = T.s; S.vx = S.vy = 0; }
    else {
      const k = 64, c = 2 * Math.sqrt(k) * .6;
      S.vx += ((T.x - S.x) * k - S.vx * c) * dt; S.x += S.vx * dt;
      S.vy += ((T.y - S.y) * k - S.vy * c) * dt; S.y += S.vy * dt;
      const ks = 70, cs = 2 * Math.sqrt(ks) * .9;
      S.vs += ((T.s - S.s) * ks - S.vs * cs) * dt; S.s += S.vs * dt;
    }
    S.s = Math.max(.05, S.s);
    const speed = Math.hypot(S.vx, S.vy), m = RM ? 0 : clamp(speed / 240, 0, 1);
    const M = MODES[T.mode || mode] || MODES.idle;
    S.t += dt; S.ph += speed * dt / (300 * S.s + 70) * Math.PI;
    const hs = Math.abs(Math.sin(S.ph)), H = BH * S.s;
    let jh = 0;
    if (S.jump >= 0) { S.jump += dt / .75; if (S.jump > 1) S.jump = -1; else jh = 4 * S.jump * (1 - S.jump) * H * .16; }
    const up = hs * H * .1 * m + jh;
    let sy = 1 + .012 * Math.sin(S.t * 2.2), sx = 1;
    if (!RM) { sy = .94 + .1 * hs; sx = 1.06 - .06 * hs; sy = 1 + (sy - 1) * m + .012 * Math.sin(S.t * 2.2) * (1 - m); sx = 1 + (sx - 1) * m; if (jh > 0) { sy += jh / H * .9; sx -= jh / H * .4; } }
    let ft = Math.abs(S.vx) > 70 ? (S.vx > 0 ? -1 : 1) : 1;
    if (RM) S.f = ft; else S.f += (ft - S.f) * Math.min(1, dt * 11);
    const sg = S.f >= 0 ? 1 : -1;
    const ry = (RM ? 0 : mouse.x * 12 + Math.sin(S.t * .9) * 3) * sg, rx = RM ? 0 : -mouse.y * 5;
    S.ry += (ry - S.ry) * Math.min(1, dt * 6); S.rx += (rx - S.rx) * Math.min(1, dt * 6);
    const et = [clamp(-S.vx * .03, -18, 18) + Math.sin(S.t * 1.8) * 3.5, clamp(-S.vx * .03, -18, 18) + Math.sin(S.t * 1.8 + 1.3) * 3.5];
    for (let i = 0; i < 2; i++) { S.ev[i] += ((et[i] - S.e[i]) * 120 - S.ev[i] * 9) * dt; S.e[i] += S.ev[i] * dt; }
    S.wt += dt * (M.fr + m * 2.5) * Math.PI * 2;
    const amp = M.amp + m * 10;
    let al = M.off[0] + amp * Math.sin(S.wt), ar = M.off[1] - amp * Math.sin(S.wt + 2.4);
    if (M.waveR) ar = M.off[1] - M.waveR * Math.sin(S.wt * 1.6) - 12;
    if (S.jump >= 0) { al += 30; ar -= 30; }
    if (RM) { al = M.off[0]; ar = M.off[1]; S.e[0] = S.e[1] = 0; }
    S.al += (al - S.al) * Math.min(1, dt * 14); S.ar += (ar - S.ar) * Math.min(1, dt * 14);
    eL.style.transform = 'rotate(' + (S.e[0] * sg).toFixed(2) + 'deg)';
    eR.style.transform = 'rotate(' + (S.e[1] * sg).toFixed(2) + 'deg)';
    aL.style.transform = 'rotate(' + (S.al * sg).toFixed(2) + 'deg)';
    aR.style.transform = 'rotate(' + (S.ar * sg).toFixed(2) + 'deg)';
    tilt.style.transform = 'translateY(' + (-up / S.s).toFixed(1) + 'px) rotate(' + (RM ? 0 : clamp(S.vx * .01, -8, 8) * -sg).toFixed(2) + 'deg) perspective(900px) rotateX(' + S.rx.toFixed(2) + 'deg) rotateY(' + S.ry.toFixed(2) + 'deg) scale(' + (S.f * sx).toFixed(3) + ',' + sy.toFixed(3) + ')';
    const sh = 1 - clamp(up / (H * .3), 0, .5);
    shadow.style.transform = 'scale(' + sh.toFixed(3) + ')'; shadow.style.opacity = sh.toFixed(3);
    bun.style.transform = 'translate3d(' + (S.x - BW / 2).toFixed(1) + 'px,' + (S.y - BH).toFixed(1) + 'px,0) scale(' + S.s.toFixed(4) + ')';
    if (speed < 26) S.calm += dt; else S.calm = 0;
    if (speed > 90 && say.classList.contains('show')) { say.classList.remove('show'); lastSay = ''; }
    if (S.calm > .45 && !override && S.init && pendingSay) { showSay(pendingSay); pendingSay = ''; }
  }
  requestAnimationFrame(tick);

  // Navigation: the rabbit runs to the link, bumps it, then the page follows.
  document.querySelectorAll('.nav nav a, .nav .account-login, .heroBtns a, .btn').forEach(a => {
    if (a.dataset.snGuide) return; a.dataset.snGuide = '1';
    a.addEventListener('click', e => {
      if (RM || e.metaKey || e.ctrlKey || e.shiftKey || e.button !== 0 || a.target === '_blank') return;
      const href = a.getAttribute('href'); if (!href || href.startsWith('javascript:')) return;
      const inPage = href.startsWith('#'); const tgt = inPage ? document.querySelector(href) : null;
      if (inPage && !tgt) return;
      e.preventDefault();
      const r = a.getBoundingClientRect(), h = mobile() ? 100 : 130;
      const low = r.top + r.height / 2 > innerHeight / 2;
      override = { x: clamp(r.left + r.width / 2, h * .32, innerWidth - h * .32), y: low ? r.top - 2 : r.bottom + h, s: h / BH, mode: 'push' };
      say.classList.remove('show');
      const t0 = performance.now();
      const go = () => {
        a.classList.add('sn-bump'); S.jump = 0; setTimeout(() => a.classList.remove('sn-bump'), 750);
        if (inPage) { tgt.scrollIntoView({ behavior: 'smooth' }); const mine = override; setTimeout(() => { if (override === mine) override = null; }, 1150); }
        else setTimeout(() => { location.href = href; }, 420);
      };
      const wait = () => { if (!override) return; if (Math.hypot(S.x - override.x, S.y - override.y) < 16 || performance.now() - t0 > 900) go(); else requestAnimationFrame(wait); };
      wait();
    });
  });
})();
