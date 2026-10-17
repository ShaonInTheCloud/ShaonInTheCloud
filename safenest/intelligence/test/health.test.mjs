import test from 'node:test';
import assert from 'node:assert/strict';
import {decide, scorePage, isParked, isChallenge, siteOf, BLOCKED_STATES} from '../src/health.mjs';
import {checkDomain} from '../src/health-runner.mjs';

const now = new Date('2026-10-18T02:00:00Z');
const day = 86400000;
const casinoPage = {alive: true, status: 200, title: 'Baji Live — Online Casino & Cricket Betting', text: 'Deposit with bKash or Nagad. Welcome bonus. Slots, Aviator, live casino.'};

test('scores English and Bangla gambling pages, ignores ordinary pages', () => {
  assert.ok(scorePage(casinoPage.title, casinoPage.text).score >= 60);
  assert.ok(scorePage('ক্রিকেট বাজি', 'বিকাশ দিয়ে জমা করুন, বোনাস পান, ক্যাসিনো').score >= 30);
  assert.ok(scorePage('Prothom Alo', 'Bangladesh news, cricket scores, weather').score < 30);
  assert.ok(isParked('example.bet', 'This domain is for sale! Buy this domain on Sedo'));
  assert.ok(isChallenge('Just a moment...', 'Checking your browser before accessing'));
  assert.equal(siteOf('www.m.baji.live'), 'baji.live');
  assert.equal(siteOf('play.example.com.bd'), 'example.com.bd');
});

test('a live gambling site stays active and is rechecked tomorrow', () => {
  const {update, discovered} = decide({domain: 'baji.live', state: 'unchecked'}, casinoPage, now);
  assert.equal(update.state, 'active');
  assert.equal(update.next_check, new Date(+now + day).toISOString());
  assert.equal(discovered, null);
  assert.ok(BLOCKED_STATES.has('active'));
});

test('dead domains go dormant (still blocked), then archived after 30 days (rechecked weekly), and revive', () => {
  let row = {domain: 'old.bet', state: 'active'};
  let {update} = decide(row, {alive: false, error: 'ENOTFOUND'}, now);
  assert.equal(update.state, 'dormant'); assert.equal(update.dead_since, now.toISOString()); assert.equal(update.fail_streak, 1);
  assert.ok(BLOCKED_STATES.has('dormant'));
  row = {...row, ...update};
  ({update} = decide(row, {alive: false}, new Date(+now + 29 * day)));
  assert.equal(update.state, 'dormant');
  ({update} = decide(row, {alive: false}, new Date(+now + 30 * day)));
  assert.equal(update.state, 'archived'); assert.equal(update.next_check, new Date(+now + 37 * day).toISOString());
  assert.ok(!BLOCKED_STATES.has('archived'));
  row = {...row, ...update};
  ({update} = decide(row, casinoPage, new Date(+now + 40 * day)));
  assert.equal(update.state, 'active', 'revived gambling site goes straight back to active');
  assert.equal(update.dead_since, null); assert.equal(update.fail_streak, 0);
});

test('a redirect to a new gambling domain is reported as a mirror to add', () => {
  const {update, discovered} = decide({domain: 'krikya.com', state: 'active'}, {...casinoPage, finalHost: 'www.krikya77.live'}, now);
  assert.equal(update.state, 'active');
  assert.equal(update.redirect_to, 'www.krikya77.live');
  assert.deepEqual(discovered, {domain: 'www.krikya77.live', source: 'redirect:krikya.com'});
  assert.equal(decide({domain: 'a.bet', state: 'active'}, {...casinoPage, finalHost: 'm.a.bet'}, now).discovered, null, 'same site is not a mirror');
});

test('a site that stops looking like gambling goes to review after 3 checks; the team can keep it', () => {
  const plain = {alive: true, status: 200, title: 'Blog', text: 'Recipes and travel'};
  let row = {domain: 'turned.bet', state: 'active', low_streak: 0};
  for (const expected of ['active', 'active', 'review']) { row = {...row, ...decide(row, plain, now).update}; assert.equal(row.state, expected); }
  assert.ok(BLOCKED_STATES.has('review'), 'review stays blocked until the team decides');
  const kept = decide({domain: 'kept.bet', state: 'active', low_streak: 5, reviewed_by_team: true}, plain, now).update;
  assert.equal(kept.state, 'active');
});

test('parked pages and bot challenges', () => {
  assert.equal(decide({domain: 'p.bet', state: 'active'}, {alive: true, title: 'p.bet', text: 'Buy this domain'}, now).update.state, 'parked');
  const ch = decide({domain: 'c.bet', state: 'active', score: 70}, {alive: true, title: 'Just a moment...', text: ''}, now).update;
  assert.equal(ch.state, 'active'); assert.equal(ch.score, 70);
});

test('checkDomain follows redirects, falls back to http, and classifies failures', async () => {
  const pages = {
    'https://a.bet': {status: 301, location: 'https://www.a-new.bet/', text: ''},
    'https://www.a-new.bet/': {status: 200, text: '<title>Casino</title><p>Deposit bKash</p>'},
  };
  const get = async url => { if (pages[url]) return pages[url]; const e = new Error('getaddrinfo ENOTFOUND'); e.code = 'ENOTFOUND'; throw e; };
  const r = await checkDomain('a.bet', get);
  assert.equal(r.alive, true); assert.equal(r.finalHost, 'www.a-new.bet'); assert.equal(r.title, 'Casino');
  assert.equal((await checkDomain('gone.bet', get)).alive, false);
  const certThenHttp = async url => { if (url.startsWith('https')) throw Object.assign(new Error('certificate has expired'), {code: 'CERT_HAS_EXPIRED'}); return {status: 200, text: '<title>Bet now</title>'}; };
  assert.equal((await checkDomain('x.bet', certThenHttp)).alive, true);
  const binary = async () => { throw new Error('Nontext response rejected'); };
  assert.equal((await checkDomain('apk.bet', binary)).alive, true);
});
