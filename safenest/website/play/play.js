/* Nest Run — SafeNest's browser game. Plain canvas 2D, no dependencies, no network.
 * The rabbit runs home through a neon night; betting pop-ups try to stop it.
 * Shields stand in for the app's on-device blocklist. Best time stays in this browser only. */
(() => {
  'use strict';
  const canvas = document.getElementById('game');
  const ctx = canvas.getContext('2d', {alpha: false});
  if (!ctx) return;
  const overlay = document.getElementById('overlay');
  const $ = id => document.getElementById(id);
  const hud = {time: $('hud-time'), dodged: $('hud-dodged'), shields: $('hud-shields')};
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  const bn = () => document.documentElement.lang === 'bn';
  const BEST_KEY = 'safenest-nest-run-best';

  // ---------- Layout: logical height 720, width follows the viewport aspect ----------
  const H = 720, GROUND = 600;
  let W = 1280, dpr = 1;
  function resize() {
    const rect = canvas.getBoundingClientRect();
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    W = Math.max(640, Math.round(rect.width / rect.height * H));
    canvas.width = Math.round(W * dpr); canvas.height = Math.round(H * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }
  resize();
  addEventListener('resize', resize);

  // ---------- Copy ----------
  const popupText = () => bn()
    ? ['এখনই বাজি ধরুন', '৫,০০০৳ বোনাস নিন', 'ফ্রি স্পিন!', '৬০ সেকেন্ডে দাবি করুন', 'লাইভ ক্যাসিনো', 'আজই জিতুন']
    : ['BET NOW', 'CLAIM ৳5,000', 'FREE SPIN!', 'CLAIM IN 60 S', 'LIVE CASINO', 'WIN TODAY'];
  const bannerText = () => bn() ? ['বড় জয়!', 'জ্যাকপট', '১০০% বোনাস', 'VIP অফার'] : ['WIN BIG', 'JACKPOT', '100% BONUS', 'VIP OFFER'];
  const blockedWord = () => bn() ? 'আটকানো' : 'BLOCKED';

  // ---------- Audio (off until the player turns it on) ----------
  let audio = null, soundOn = false;
  function beep(freq, dur = 0.08, type = 'square', gain = 0.04) {
    if (!soundOn) return;
    try {
      audio ??= new (window.AudioContext || window.webkitAudioContext)();
      if (audio.state === 'suspended') audio.resume();
      const o = audio.createOscillator(), g = audio.createGain();
      o.type = type; o.frequency.setValueAtTime(freq, audio.currentTime);
      g.gain.setValueAtTime(gain, audio.currentTime);
      g.gain.exponentialRampToValueAtTime(0.0001, audio.currentTime + dur);
      o.connect(g).connect(audio.destination); o.start(); o.stop(audio.currentTime + dur);
    } catch { soundOn = false; }
  }

  // ---------- State ----------
  const state = {
    mode: 'title', // title | running | paused | over
    t: 0, speed: 420, distance: 0, dodged: 0, blocked: 0, shields: 0,
    best: 0, shake: 0, nextSpawn: 0, nextShield: 0,
    rabbit: {x: 220, y: GROUND, vy: 0, h: 92, w: 58, sliding: false, slideT: 0, onGround: true, frame: 0, hurt: 0},
    obstacles: [], pickups: [], particles: [], floaters: [],
  };
  try { state.best = Number(localStorage.getItem(BEST_KEY)) || 0; } catch {}
  const bestLine = $('best');
  function showBest() {
    if (state.best > 0) {
      bestLine.hidden = false;
      bestLine.textContent = (bn() ? 'এই ব্রাউজারে সেরা দৌড়: ' : 'Best run in this browser: ') + state.best.toFixed(1) + ' s';
    }
  }
  showBest();

  function reset() {
    Object.assign(state, {t: 0, speed: 420, distance: 0, dodged: 0, blocked: 0, shields: 0, shake: 0,
      nextSpawn: 900, nextShield: 2600, obstacles: [], pickups: [], particles: [], floaters: []});
    Object.assign(state.rabbit, {y: GROUND, vy: 0, sliding: false, slideT: 0, onGround: true, frame: 0, hurt: 0});
  }
  function setMode(mode) {
    state.mode = mode;
    overlay.dataset.state = mode;
    for (const card of overlay.children) card.hidden = !card.matches(`.${mode === 'running' ? 'none' : mode}-card`);
  }
  function start() { reset(); setMode('running'); beep(660, .1, 'triangle'); last = performance.now(); }
  function pause() { if (state.mode === 'running') { setMode('paused'); $('resume').focus(); } }
  function resume() { if (state.mode === 'paused') { setMode('running'); last = performance.now(); } }
  function gameOver() {
    state.shake = reducedMotion.matches ? 0 : 14;
    beep(140, .35, 'sawtooth', .06);
    const seconds = state.t;
    if (seconds > state.best) { state.best = seconds; try { localStorage.setItem(BEST_KEY, String(seconds.toFixed(1))); } catch {} }
    $('over-seconds').textContent = seconds.toFixed(1);
    $('stat-dodged').textContent = String(state.dodged);
    $('stat-blocked').textContent = String(state.blocked);
    $('stat-best').textContent = state.best.toFixed(1) + ' s';
    showBest();
    setMode('over');
    setTimeout(() => $('again').focus(), 50);
  }

  // ---------- Input ----------
  let jumpHeld = false, slideHeld = false, jumpQueued = 0;
  function jump() {
    const r = state.rabbit;
    if (state.mode !== 'running') return;
    if (r.onGround) { r.vy = -1180; r.onGround = false; r.sliding = false; beep(520, .07, 'triangle'); }
    else jumpQueued = 0.12; // buffered: lands and jumps immediately
  }
  function slide(on) {
    slideHeld = on;
    const r = state.rabbit;
    if (on && state.mode === 'running') {
      if (!r.onGround) r.vy = Math.max(r.vy, 1400); // slam down
      else if (!r.sliding) { r.sliding = true; r.slideT = 0; beep(300, .06, 'square', .03); }
    }
  }
  addEventListener('keydown', e => {
    if (e.repeat) return;
    if (e.code === 'Space' || e.code === 'ArrowUp' || e.code === 'KeyW') {
      e.preventDefault(); jumpHeld = true;
      if (state.mode === 'title' || state.mode === 'over') start(); else if (state.mode === 'paused') resume(); else jump();
    } else if (e.code === 'ArrowDown' || e.code === 'KeyS') { e.preventDefault(); slide(true); }
    else if (e.code === 'Escape' || e.code === 'KeyP') { state.mode === 'running' ? pause() : resume(); }
    else if (e.code === 'Enter' && (state.mode === 'title' || state.mode === 'over')) start();
  });
  addEventListener('keyup', e => {
    if (e.code === 'Space' || e.code === 'ArrowUp' || e.code === 'KeyW') jumpHeld = false;
    if (e.code === 'ArrowDown' || e.code === 'KeyS') slide(false);
  });
  // Touch: tap = jump, swipe down = slide, plus two invisible halves on coarse pointers.
  let touchStart = null;
  canvas.addEventListener('pointerdown', e => {
    touchStart = {x: e.clientX, y: e.clientY, t: performance.now()};
    if (state.mode === 'running') { jump(); jumpHeld = true; }
  });
  canvas.addEventListener('pointermove', e => {
    if (!touchStart || state.mode !== 'running') return;
    if (e.clientY - touchStart.y > 40) { slide(true); touchStart = null; setTimeout(() => slide(false), 700); }
  });
  const release = () => { touchStart = null; jumpHeld = false; };
  canvas.addEventListener('pointerup', release); canvas.addEventListener('pointercancel', release);
  const touchJump = $('touch-jump'), touchSlide = $('touch-slide');
  touchJump.addEventListener('pointerdown', e => { e.preventDefault(); jumpHeld = true; jump(); });
  touchJump.addEventListener('pointerup', () => { jumpHeld = false; });
  touchSlide.addEventListener('pointerdown', e => { e.preventDefault(); slide(true); });
  touchSlide.addEventListener('pointerup', () => slide(false));
  touchSlide.addEventListener('pointercancel', () => slide(false));
  $('start').addEventListener('click', start);
  $('again').addEventListener('click', start);
  $('resume').addEventListener('click', resume);
  $('pause').addEventListener('click', () => state.mode === 'running' ? pause() : resume());
  $('mute').addEventListener('click', e => {
    soundOn = !soundOn; e.currentTarget.setAttribute('aria-pressed', String(soundOn));
    e.currentTarget.setAttribute('aria-label', soundOn ? 'Sound off' : 'Sound on'); beep(880, .06, 'triangle');
  });
  document.addEventListener('visibilitychange', () => { if (document.hidden) pause(); });

  // ---------- Spawning ----------
  const rand = (a, b) => a + Math.random() * (b - a);
  const pick = list => list[Math.floor(Math.random() * list.length)];
  function spawnObstacle() {
    const roll = Math.random(), x = W + 80;
    if (roll < 0.42) { // pop-up window on the ground: jump over it
      const w = rand(96, 150), h = rand(78, 118);
      state.obstacles.push({kind: 'popup', x, y: GROUND - h, w, h, label: pick(popupText()), wobble: rand(0, 6.28)});
    } else if (roll < 0.68) { // floating banner at head height: slide under it
      const w = rand(170, 260), h = 54, y = GROUND - 150;
      state.obstacles.push({kind: 'banner', x, y, w, h, label: pick(bannerText()), wobble: rand(0, 6.28)});
    } else if (roll < 0.86) { // slot bot: tall, jump
      state.obstacles.push({kind: 'slot', x, y: GROUND - 132, w: 74, h: 132, label: '7 7 7', wobble: rand(0, 6.28), spin: 0});
    } else { // two small pop-ups in a row
      const h = rand(60, 84);
      state.obstacles.push({kind: 'popup', x, y: GROUND - h, w: 84, h, label: pick(popupText()), wobble: 0});
      state.obstacles.push({kind: 'popup', x: x + 150, y: GROUND - h, w: 84, h, label: pick(popupText()), wobble: 1.5});
    }
  }
  function spawnShield() {
    const high = Math.random() < 0.5;
    state.pickups.push({x: W + 60, y: high ? GROUND - 250 : GROUND - 70, r: 20, bob: rand(0, 6.28)});
  }
  function burst(x, y, color, n = 18) {
    const count = reducedMotion.matches ? Math.ceil(n / 3) : n;
    for (let i = 0; i < count; i++) {
      const a = rand(0, Math.PI * 2), s = rand(120, 460);
      state.particles.push({x, y, vx: Math.cos(a) * s, vy: Math.sin(a) * s - 120, life: rand(.4, .9), color, size: rand(3, 8)});
    }
  }

  // ---------- Update ----------
  function hitbox() {
    const r = state.rabbit, h = r.sliding ? 46 : r.h;
    return {x: r.x - r.w / 2 + 8, y: r.y - h, w: r.w - 16, h: h - 4};
  }
  const overlaps = (a, b) => a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
  function update(dt) {
    const s = state, r = s.rabbit;
    s.t += dt;
    s.speed = Math.min(1100, 420 + s.t * 22 + Math.pow(s.t, 1.25) * 2.2);
    const dx = s.speed * dt;
    s.distance += dx;
    // Rabbit physics
    if (!r.onGround) {
      const gravity = jumpHeld && r.vy < 0 ? 2300 : 3400; // hold to jump a little higher
      r.vy += gravity * dt; r.y += r.vy * dt;
      if (r.y >= GROUND) { r.y = GROUND; r.vy = 0; r.onGround = true; if (jumpQueued > 0) { jumpQueued = 0; jump(); } }
    }
    if (jumpQueued > 0) jumpQueued -= dt;
    if (r.sliding) { r.slideT += dt; if ((!slideHeld && r.slideT > 0.25) || r.slideT > 0.9) r.sliding = false; }
    r.frame += dt * (10 + s.speed / 90);
    if (r.hurt > 0) r.hurt -= dt;
    // Spawn by distance so the rhythm scales with speed
    s.nextSpawn -= dx;
    if (s.nextSpawn <= 0) { spawnObstacle(); s.nextSpawn = rand(520, 820) + s.speed * rand(0.35, 0.65); }
    s.nextShield -= dx;
    if (s.nextShield <= 0) { spawnShield(); s.nextShield = rand(2400, 4200); }
    // Obstacles
    const box = hitbox();
    for (let i = s.obstacles.length - 1; i >= 0; i--) {
      const o = s.obstacles[i];
      o.x -= dx;
      if (o.kind === 'slot') o.spin += dt * 9;
      if (o.x + o.w < -40) { s.obstacles.splice(i, 1); continue; }
      if (!o.passed && o.x + o.w < r.x - r.w / 2) { o.passed = true; s.dodged++; beep(720 + Math.min(600, s.dodged * 6), .04, 'triangle', .02); }
      const bob = o.kind === 'banner' ? Math.sin(s.t * 3 + o.wobble) * 8 : 0;
      if (overlaps(box, {x: o.x + 6, y: o.y + bob + 6, w: o.w - 12, h: o.h - 10})) {
        if (s.shields > 0) {
          s.shields--; s.blocked++; s.obstacles.splice(i, 1);
          burst(o.x + o.w / 2, o.y + o.h / 2, '#ff2d87', 26);
          s.floaters.push({x: o.x + o.w / 2, y: o.y, text: blockedWord(), life: 1});
          beep(980, .12, 'square', .05); r.hurt = 0.15;
        } else { gameOver(); return; }
      }
    }
    for (let i = s.pickups.length - 1; i >= 0; i--) {
      const p = s.pickups[i];
      p.x -= dx;
      if (p.x < -60) { s.pickups.splice(i, 1); continue; }
      const py = p.y + Math.sin(s.t * 4 + p.bob) * 10;
      if (overlaps(box, {x: p.x - p.r, y: py - p.r, w: p.r * 2, h: p.r * 2})) {
        s.pickups.splice(i, 1);
        if (s.shields < 3) { s.shields++; burst(p.x, py, '#c7b3ff', 14); beep(1320, .1, 'triangle', .04); }
        else { s.floaters.push({x: p.x, y: py - 10, text: '+', life: .6}); beep(1320, .05, 'triangle', .02); }
      }
    }
    for (let i = s.particles.length - 1; i >= 0; i--) {
      const p = s.particles[i];
      p.life -= dt; p.vy += 900 * dt; p.x += (p.vx - s.speed * .4) * dt; p.y += p.vy * dt;
      if (p.life <= 0) s.particles.splice(i, 1);
    }
    for (let i = s.floaters.length - 1; i >= 0; i--) {
      const f = s.floaters[i]; f.life -= dt; f.y -= 70 * dt; f.x -= dx * .5;
      if (f.life <= 0) s.floaters.splice(i, 1);
    }
    if (s.shake > 0) s.shake = Math.max(0, s.shake - dt * 40);
  }

  // ---------- Drawing ----------
  const cityLayers = [
    {speed: .12, color: '#2a1033', top: 300, seed: 3, scale: 1.0},
    {speed: .28, color: '#3b1547', top: 380, seed: 7, scale: .8},
    {speed: .55, color: '#4d1a5c', top: 470, seed: 11, scale: .6},
  ];
  function buildings(layer) {
    // Deterministic skyline per layer; repeats every 1600 units.
    if (layer.cache) return layer.cache;
    const list = []; let x = 0, k = layer.seed;
    const r = () => { k = (k * 9301 + 49297) % 233280; return k / 233280; };
    while (x < 1600) {
      const w = 50 + r() * 120, h = 60 + r() * 220 * layer.scale;
      const windows = [];
      for (let wy = 18; wy < h - 10; wy += 26) for (let wx = 10; wx < w - 12; wx += 22) if (r() < .45) windows.push([wx, wy, r() < .25 ? '#ff2d87' : '#c7b3ff']);
      list.push({x, w, h, windows, sign: r() < .18});
      x += w + 10 + r() * 40;
    }
    return layer.cache = list;
  }
  function drawBackground() {
    const sky = ctx.createLinearGradient(0, 0, 0, H);
    sky.addColorStop(0, '#120717'); sky.addColorStop(.55, '#1a0b1f'); sky.addColorStop(1, '#3a1047');
    ctx.fillStyle = sky; ctx.fillRect(0, 0, W, H);
    // Stars
    ctx.fillStyle = '#ffffff55';
    for (let i = 0; i < 60; i++) { const x = (i * 137.5 + state.distance * .02) % (W + 40) - 20, y = (i * 53) % 260 + 20; ctx.fillRect(x, y, 2, 2); }
    // Moon
    ctx.fillStyle = '#ffd6e7'; ctx.beginPath(); ctx.arc(W - 180, 120, 46, 0, 7); ctx.fill();
    ctx.fillStyle = '#1a0b1f'; ctx.beginPath(); ctx.arc(W - 160, 108, 40, 0, 7); ctx.fill();
    // City layers
    for (const layer of cityLayers) {
      const offset = (state.distance * layer.speed) % 1600;
      for (let rep = -1; rep * 1600 - offset < W + 200; rep++) {
        for (const b of buildings(layer)) {
          const x = b.x + rep * 1600 - offset; if (x + b.w < 0 || x > W) continue;
          const y = GROUND - layer.top + (300 - b.h) * 0; const top = GROUND - b.h - (layer.top - 300);
          ctx.fillStyle = layer.color; ctx.fillRect(x, top, b.w, b.h + (layer.top - 300));
          for (const [wx, wy, c] of b.windows) { ctx.fillStyle = c; ctx.globalAlpha = .55; ctx.fillRect(x + wx, top + wy, 8, 12); }
          ctx.globalAlpha = 1;
          if (b.sign && layer.speed > .2) { ctx.fillStyle = '#ff2d87'; ctx.fillRect(x + 6, top - 10, b.w - 12, 6); }
          void y;
        }
      }
    }
    // Ground with neon edge and reflection
    const groundG = ctx.createLinearGradient(0, GROUND, 0, H);
    groundG.addColorStop(0, '#2c0f36'); groundG.addColorStop(1, '#160a1b');
    ctx.fillStyle = groundG; ctx.fillRect(0, GROUND, W, H - GROUND);
    ctx.fillStyle = '#ff2d87'; ctx.shadowColor = '#ff2d87'; ctx.shadowBlur = 18; ctx.fillRect(0, GROUND - 2, W, 4); ctx.shadowBlur = 0;
    ctx.strokeStyle = '#ffffff14'; ctx.lineWidth = 2; ctx.beginPath();
    for (let x = -((state.distance * 1) % 90); x < W; x += 90) { ctx.moveTo(x, GROUND + 30); ctx.lineTo(x - 30, H); }
    ctx.stroke();
  }
  function roundRect(x, y, w, h, r) { ctx.beginPath(); ctx.roundRect(x, y, w, h, r); }
  function drawPopup(o) {
    const bob = Math.sin(state.t * 6 + o.wobble) * 2;
    ctx.save(); ctx.translate(o.x, o.y + bob);
    ctx.shadowColor = '#ff2d87aa'; ctx.shadowBlur = 16;
    ctx.fillStyle = '#fff4f8'; roundRect(0, 0, o.w, o.h, 10); ctx.fill(); ctx.shadowBlur = 0;
    ctx.fillStyle = '#c2185b'; roundRect(0, 0, o.w, 20, [10, 10, 0, 0]); ctx.fill();
    ctx.fillStyle = '#fff'; ctx.font = '700 11px Manrope, sans-serif'; ctx.textBaseline = 'middle'; ctx.textAlign = 'left';
    ctx.fillText('✕', o.w - 16, 10);
    ctx.fillStyle = '#2b1a2f'; ctx.textAlign = 'center';
    ctx.font = `800 ${Math.min(15, o.w / 7.5)}px "Anek Bangla", "Noto Sans Bengali", Manrope, sans-serif`;
    wrapText(o.label, o.w / 2, 20 + (o.h - 20) * .42, o.w - 14, 16);
    ctx.fillStyle = '#ff2d87'; roundRect(10, o.h - 26, o.w - 20, 16, 8); ctx.fill();
    ctx.restore();
  }
  function wrapText(text, x, y, maxW, lh) {
    const words = text.split(' '); let line = '', lines = [];
    for (const w of words) { const test = line ? line + ' ' + w : w; if (ctx.measureText(test).width > maxW && line) { lines.push(line); line = w; } else line = test; }
    lines.push(line);
    lines.forEach((l, i) => ctx.fillText(l, x, y + (i - (lines.length - 1) / 2) * lh));
  }
  function drawBanner(o) {
    const bob = Math.sin(state.t * 3 + o.wobble) * 8;
    ctx.save(); ctx.translate(o.x, o.y + bob);
    ctx.shadowColor = '#ffb3d1'; ctx.shadowBlur = 20;
    ctx.fillStyle = '#ff2d87'; roundRect(0, 0, o.w, o.h, 27); ctx.fill(); ctx.shadowBlur = 0;
    ctx.strokeStyle = '#ffffffaa'; ctx.lineWidth = 2; roundRect(5, 5, o.w - 10, o.h - 10, 22); ctx.stroke();
    ctx.fillStyle = '#fff'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    ctx.font = '800 22px "Anek Bangla", "Noto Sans Bengali", Manrope, sans-serif';
    ctx.fillText(o.label, o.w / 2, o.h / 2 + 1);
    // little tail: it is pretending to be a notification
    ctx.fillStyle = '#ff2d87'; ctx.beginPath(); ctx.moveTo(o.w / 2 - 10, o.h - 2); ctx.lineTo(o.w / 2, o.h + 12); ctx.lineTo(o.w / 2 + 10, o.h - 2); ctx.fill();
    ctx.restore();
  }
  function drawSlot(o) {
    ctx.save(); ctx.translate(o.x, o.y);
    ctx.fillStyle = '#5a1f6b'; roundRect(0, 0, o.w, o.h, 12); ctx.fill();
    ctx.fillStyle = '#c7b3ff'; roundRect(8, 10, o.w - 16, 44, 6); ctx.fill();
    ctx.fillStyle = '#2b1a2f'; ctx.font = '800 20px Manrope, sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
    const sym = ['7', '✕', '◆', '7'];
    for (let i = 0; i < 3; i++) ctx.fillText(sym[Math.floor(o.spin + i * 1.3) % sym.length], 20 + i * 17, 32);
    ctx.fillStyle = '#ff2d87'; ctx.beginPath(); ctx.arc(o.w + 6, 24, 8, 0, 7); ctx.fill(); ctx.fillRect(o.w - 4, 24, 10, 40);
    // legs: it walks toward you
    ctx.fillStyle = '#3b1547';
    const step = Math.sin(state.t * 14) * 6;
    ctx.fillRect(12, o.h - 4, 14, 8 + step); ctx.fillRect(o.w - 26, o.h - 4, 14, 8 - step);
    ctx.fillStyle = '#ff2d87'; ctx.fillRect(10, 66, o.w - 20, 6); ctx.fillRect(10, 80, o.w - 20, 6); ctx.fillRect(10, 94, o.w - 20, 6);
    ctx.restore();
  }
  function drawShield(p) {
    const y = p.y + Math.sin(state.t * 4 + p.bob) * 10;
    ctx.save(); ctx.translate(p.x, y);
    ctx.shadowColor = '#c7b3ff'; ctx.shadowBlur = 22;
    ctx.fillStyle = '#c7b3ff'; ctx.beginPath();
    ctx.moveTo(0, -22); ctx.lineTo(19, -12); ctx.lineTo(16, 8); ctx.lineTo(0, 22); ctx.lineTo(-16, 8); ctx.lineTo(-19, -12); ctx.closePath(); ctx.fill();
    ctx.shadowBlur = 0; ctx.fillStyle = '#1a0b1f'; ctx.beginPath();
    ctx.moveTo(0, -13); ctx.lineTo(11, -7); ctx.lineTo(9, 5); ctx.lineTo(0, 13); ctx.lineTo(-9, 5); ctx.lineTo(-11, -7); ctx.closePath(); ctx.fill();
    ctx.strokeStyle = '#fff'; ctx.lineWidth = 3; ctx.beginPath(); ctx.moveTo(-5, 0); ctx.lineTo(-1, 4); ctx.lineTo(6, -5); ctx.stroke();
    ctx.restore();
  }
  function drawRabbit() {
    const r = state.rabbit, run = r.onGround && !r.sliding;
    const bounce = run ? Math.abs(Math.sin(r.frame)) * 5 : 0;
    ctx.save(); ctx.translate(r.x, r.y - bounce);
    if (r.hurt > 0) ctx.globalAlpha = .6 + Math.sin(r.hurt * 60) * .3;
    // shield aura
    if (state.shields > 0) { ctx.strokeStyle = '#c7b3ff'; ctx.lineWidth = 3; ctx.setLineDash([8, 7]); ctx.lineDashOffset = -state.t * 60;
      ctx.beginPath(); ctx.ellipse(0, -46, 48, 58, 0, 0, 7); ctx.stroke(); ctx.setLineDash([]); }
    const brown = '#8b5a3c', dark = '#5d3a25', pink = '#ff2d87', cream = '#f3dccb';
    if (r.sliding) {
      ctx.scale(1.25, .6);
    }
    // legs
    const leg = run ? Math.sin(r.frame) * 18 : (r.onGround ? 0 : -8);
    ctx.fillStyle = dark;
    ctx.fillRect(-18 + leg * .5, -30, 14, 30); ctx.fillRect(4 - leg * .5, -30, 14, 30);
    ctx.fillStyle = brown;
    ctx.beginPath(); ctx.ellipse(-16 + leg * .6, -2, 14, 7, 0, 0, 7); ctx.fill();
    ctx.beginPath(); ctx.ellipse(10 - leg * .6, -2, 14, 7, 0, 0, 7); ctx.fill();
    // tail
    ctx.fillStyle = cream; ctx.beginPath(); ctx.arc(-30, -40, 9, 0, 7); ctx.fill();
    // body in a pink tee
    ctx.fillStyle = brown; ctx.beginPath(); ctx.ellipse(0, -46, 26, 30, 0, 0, 7); ctx.fill();
    ctx.fillStyle = pink; ctx.beginPath(); ctx.ellipse(0, -44, 24, 22, 0, 0, 7); ctx.fill();
    ctx.fillStyle = '#fff'; ctx.font = '800 10px Manrope, sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle'; ctx.fillText('SN', 2, -46);
    // arm pumping
    ctx.fillStyle = brown; ctx.save(); ctx.translate(6, -56); ctx.rotate(Math.sin(r.frame + 1) * .8 - .3); ctx.fillRect(-4, 0, 8, 22); ctx.restore();
    // head
    ctx.fillStyle = brown; ctx.beginPath(); ctx.ellipse(12, -84, 22, 19, 0, 0, 7); ctx.fill();
    // ears (trail behind when fast)
    const earLean = Math.min(.9, state.speed / 1400) + (r.onGround ? 0 : .3);
    for (const [ox, tilt] of [[-2, -.5], [8, -.2]]) {
      ctx.save(); ctx.translate(ox, -98); ctx.rotate(-earLean + tilt);
      ctx.fillStyle = brown; ctx.beginPath(); ctx.ellipse(0, -22, 7, 26, 0, 0, 7); ctx.fill();
      ctx.fillStyle = '#ffb3d1'; ctx.beginPath(); ctx.ellipse(0, -22, 3.5, 18, 0, 0, 7); ctx.fill();
      ctx.restore();
    }
    // face
    ctx.fillStyle = '#1a0b1f'; ctx.beginPath(); ctx.arc(24, -88, 3.2, 0, 7); ctx.fill();
    ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.arc(25, -89, 1.1, 0, 7); ctx.fill();
    ctx.fillStyle = '#ff6fa8'; ctx.beginPath(); ctx.arc(33, -80, 2.6, 0, 7); ctx.fill();
    ctx.fillStyle = cream; ctx.beginPath(); ctx.ellipse(27, -76, 7, 5, 0, 0, 7); ctx.fill();
    ctx.restore();
  }
  function drawEffects() {
    for (const p of state.particles) { ctx.globalAlpha = Math.max(0, p.life); ctx.fillStyle = p.color; ctx.fillRect(p.x, p.y, p.size, p.size); }
    ctx.globalAlpha = 1;
    for (const f of state.floaters) {
      ctx.globalAlpha = Math.min(1, f.life * 1.5); ctx.fillStyle = '#fff'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.font = '800 26px "Anek Bangla", "Noto Sans Bengali", Manrope, sans-serif';
      ctx.shadowColor = '#ff2d87'; ctx.shadowBlur = 14; ctx.fillText(f.text, f.x, f.y); ctx.shadowBlur = 0;
    }
    ctx.globalAlpha = 1;
  }
  function draw() {
    ctx.save();
    if (state.shake > 0) ctx.translate(rand(-state.shake, state.shake), rand(-state.shake, state.shake));
    drawBackground();
    for (const p of state.pickups) drawShield(p);
    for (const o of state.obstacles) o.kind === 'popup' ? drawPopup(o) : o.kind === 'banner' ? drawBanner(o) : drawSlot(o);
    drawRabbit();
    drawEffects();
    // speed streaks when fast
    if (state.mode === 'running' && state.speed > 700 && !reducedMotion.matches) {
      ctx.strokeStyle = '#ffffff22'; ctx.lineWidth = 2; ctx.beginPath();
      for (let i = 0; i < 8; i++) { const y = (i * 97 + state.distance * .3) % (GROUND - 60) + 30, x = W - ((state.distance * 3 + i * 300) % (W + 200)); ctx.moveTo(x, y); ctx.lineTo(x + 120, y); }
      ctx.stroke();
    }
    ctx.restore();
  }

  // ---------- Loop ----------
  let last = performance.now(), hudTick = 0;
  function frame(now) {
    // Clamp at 0: a frame timestamp can precede the performance.now() taken in start(),
    // and a negative dt would make Math.pow(t, 1.25) NaN and poison the whole run.
    const dt = Math.min(0.05, Math.max(0, (now - last) / 1000)); last = now;
    if (state.mode === 'running') {
      update(dt);
      hudTick += dt;
      if (hudTick > 0.1) { hudTick = 0; hud.time.value = state.t.toFixed(1) + ' s'; hud.dodged.value = String(state.dodged); hud.shields.value = String(state.shields); }
    } else if (state.mode === 'title') {
      // idle: the rabbit jogs in place while the city drifts by
      state.distance += 90 * dt; state.rabbit.frame += dt * 7; state.t = 0;
    }
    draw();
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
})();
