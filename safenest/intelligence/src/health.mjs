// Smart blocklist health — pure decision logic (no network, no database), so it is fully unit-tested.
// The runner (health-runner.mjs) does the I/O and calls these functions.

const DAY = 86400000;
export const ARCHIVE_AFTER_DAYS = 30;
export const GAMBLING_THRESHOLD = 30;
export const LOW_CHECKS_BEFORE_REVIEW = 3;

// Weighted signal groups in English and Bangla. A group counts once however often it appears.
const GROUPS = [
  ['casino', 20, /\b(online casino|live casino|casino)\b|ক্যাসিনো/],
  ['sports_betting', 20, /\b(sportsbook|sports betting|place (a|your) bet|bet now|betting)\b|বাজি|বেটিং|ক্রিকেট বাজি/],
  ['games', 15, /\b(slots?|roulette|blackjack|baccarat|teen ?patti|aviator|crash game|poker)\b|স্লট|তিন পাত্তি/],
  ['money', 15, /\b(deposit|withdraw(al)?|cash ?out)\b|জমা|উত্তোলন|ডিপোজিট/],
  ['bd_payments', 15, /\b(bkash|nagad|rocket|upay)\b|বিকাশ|নগদ/],
  ['promo', 10, /\b(welcome bonus|free spins?|jackpot|odds|cashback|referral bonus)\b|বোনাস|জ্যাকপট/],
  ['age_gate', 5, /\b18\+|responsible gambling|gamble responsibly/],
];
const PARKED = /\b(this domain (is|may be) for sale|buy this domain|domain (is )?parked|parked (free|domain)|sedo|dan\.com|afternic|hugedomains|godaddy\s+(auctions|domain))\b/i;
const CHALLENGE = /\b(just a moment|checking your browser|attention required|cf-browser-verification|ddos protection by|enable javascript and cookies)\b/i;

/** Gambling score 0–100 from a page's title and visible text, with the groups that matched. */
export function scorePage(title = '', text = '') {
  const content = (title + ' ' + text).slice(0, 200000).toLowerCase();
  const signals = [];
  let score = 0;
  for (const [name, weight, pattern] of GROUPS) if (pattern.test(content)) { signals.push(name); score += weight; }
  if (/\b(bet|casino|slot|poker|jackpot)\b|বাজি|ক্যাসিনো/i.test(title)) { score += 10; signals.push('title'); }
  return {score: Math.min(100, score), signals};
}

export const isParked = (title = '', text = '') => PARKED.test(title + ' ' + text.slice(0, 50000));
export const isChallenge = (title = '', text = '') => CHALLENGE.test(title + ' ' + text.slice(0, 20000));

/** Registrable-ish name for comparing redirect targets ("www.x.com" and "m.x.com" are the same site). */
export function siteOf(host) {
  const parts = String(host).toLowerCase().replace(/\.$/, '').split('.');
  const twoLevel = /^(co|com|net|org|gov|edu|ac)$/.test(parts.at(-2) ?? '') && parts.length >= 3;
  return parts.slice(twoLevel ? -3 : -2).join('.');
}

const plusDays = (now, d) => new Date(+now + d * DAY).toISOString();

/**
 * New state for a domain after one check.
 * result: {alive, status?, finalHost?, title?, text?, error?}
 *   alive=false when the name does not resolve, refuses connections, or times out.
 * Returns {update, discovered}: the row fields to write, and a mirror domain to add (or null).
 */
export function decide(row, result, now = new Date()) {
  const iso = now.toISOString();
  const base = {domain: row.domain, last_checked: iso, http_status: result.status ?? null, last_error: result.error ? String(result.error).slice(0, 200) : null};
  if (!result.alive) {
    const deadSince = row.dead_since ?? iso;
    const archived = +now - Date.parse(deadSince) >= ARCHIVE_AFTER_DAYS * DAY;
    return {discovered: null, update: {...base, state: archived ? 'archived' : 'dormant', dead_since: deadSince,
      fail_streak: (row.fail_streak ?? 0) + 1, next_check: plusDays(now, archived ? 7 : 1), score: row.score ?? null, signals: row.signals ?? []}};
  }
  const alive = {...base, last_alive: iso, dead_since: null, fail_streak: 0};
  const title = result.title ?? '', text = result.text ?? '';
  if (isParked(title, text)) return {discovered: null, update: {...alive, state: 'parked', low_streak: 0, score: 0, signals: ['parked'], next_check: plusDays(now, 7)}};
  // Bot challenges (Cloudflare "Just a moment") hide the page: alive, but no verdict. Keep the current state.
  if (isChallenge(title, text)) {
    const keep = ['unchecked', 'archived', 'dormant', 'parked'].includes(row.state) ? 'active' : row.state;
    return {discovered: null, update: {...alive, state: keep, low_streak: row.low_streak ?? 0, score: row.score ?? null, signals: ['challenge'], next_check: plusDays(now, 1)}};
  }
  const {score, signals} = scorePage(title, text);
  const finalHost = result.finalHost ? String(result.finalHost).toLowerCase() : null;
  const movedSite = finalHost && siteOf(finalHost) !== siteOf(row.domain);
  const gambling = score >= GAMBLING_THRESHOLD;
  const discovered = movedSite && gambling ? {domain: finalHost, source: `redirect:${row.domain}`.slice(0, 300)} : null;
  if (gambling) return {discovered, update: {...alive, state: 'active', low_streak: 0, score, signals, redirect_to: movedSite ? finalHost : null, next_check: plusDays(now, 1)}};
  const lowStreak = (row.low_streak ?? 0) + 1;
  // Many betting sites render with JavaScript, so one thin page is not proof. Review after 3 low checks in a row,
  // or at once when an archived/parked name comes back without gambling content. The team's "keep" decision sticks.
  const state = row.reviewed_by_team && row.state === 'active' ? 'active'
    : lowStreak >= LOW_CHECKS_BEFORE_REVIEW || ['review', 'archived', 'parked'].includes(row.state) ? 'review' : 'active';
  return {discovered: null, update: {...alive, state, low_streak: lowStreak, score, signals, redirect_to: movedSite ? finalHost : null, next_check: plusDays(now, state === 'review' ? 3 : 1)}};
}

/** States whose domains are blocked by the apps and the iPhone DNS filter. */
export const BLOCKED_STATES = new Set(['unchecked', 'active', 'dormant', 'review']);
