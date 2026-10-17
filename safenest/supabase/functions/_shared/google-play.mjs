/**
 * Google Play Developer API client for subscription verification.
 * Service-account credentials stay server-side (PLAY_SERVICE_ACCOUNT_JSON).
 * No client-supplied field is trusted: the purchase token is only a lookup key
 * and every entitlement fact comes from Google's signed API response.
 */
import {PaymentError} from './payments.mjs';

const SCOPE = 'https://www.googleapis.com/auth/androidpublisher';
const TOKEN_URL = 'https://oauth2.googleapis.com/token';
const API = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications';
export const PURCHASE_TOKEN = /^[A-Za-z0-9._-]{20,4096}$/;
export const PLAN_CODES = new Set(['weekly', 'monthly', 'quarterly', 'annual']);

const b64url = bytes => btoa(String.fromCharCode(...new Uint8Array(bytes)))
  .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const pemToDer = pem => Uint8Array.from(atob(pem.replace(/-----[A-Z ]+-----/g, '').replace(/\s+/g, '')), c => c.charCodeAt(0));

export function playConfig(env = {}) {
  const packageName = env.PLAY_PACKAGE_NAME || '';
  if (!/^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$/.test(packageName)) throw new PaymentError('play_not_configured');
  let account;
  try { account = JSON.parse(env.PLAY_SERVICE_ACCOUNT_JSON || ''); } catch { throw new PaymentError('play_not_configured'); }
  if (!account || typeof account.client_email !== 'string' || typeof account.private_key !== 'string') {
    throw new PaymentError('play_not_configured');
  }
  return {packageName, clientEmail: account.client_email, privateKey: account.private_key};
}

/** Mint a short-lived OAuth2 access token with a signed service-account JWT. */
export async function accessToken(config, fetcher = fetch, now = Date.now()) {
  const header = b64url(new TextEncoder().encode(JSON.stringify({alg: 'RS256', typ: 'JWT'})));
  const iat = Math.floor(now / 1000);
  const claims = b64url(new TextEncoder().encode(JSON.stringify({
    iss: config.clientEmail, scope: SCOPE, aud: TOKEN_URL, iat, exp: iat + 600,
  })));
  const key = await crypto.subtle.importKey('pkcs8', pemToDer(config.privateKey),
    {name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256'}, false, ['sign']);
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(`${header}.${claims}`));
  const assertion = `${header}.${claims}.${b64url(signature)}`;
  const response = await fetcher(TOKEN_URL, {
    method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
    body: new URLSearchParams({grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion}),
    signal: AbortSignal.timeout(10_000), redirect: 'error',
  });
  if (!response.ok) throw new PaymentError('play_auth_unavailable');
  const body = await response.json();
  if (typeof body.access_token !== 'string' || !body.access_token) throw new PaymentError('play_auth_unavailable');
  return body.access_token;
}

/** purchases.subscriptionsv2.get — the only source of truth for subscription state. */
export async function fetchSubscription(config, token, purchaseToken, fetcher = fetch) {
  if (!PURCHASE_TOKEN.test(purchaseToken)) throw new PaymentError('invalid_purchase_token', 400);
  const url = `${API}/${encodeURIComponent(config.packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
  const response = await fetcher(url, {headers: {Authorization: `Bearer ${token}`}, signal: AbortSignal.timeout(10_000), redirect: 'error'});
  if (response.status === 404 || response.status === 400) throw new PaymentError('purchase_not_found', 404);
  if (!response.ok) throw new PaymentError('play_verification_unavailable');
  const raw = await response.text();
  if (raw.length > 65_536) throw new PaymentError('play_verification_unavailable');
  let body;
  try { body = JSON.parse(raw); } catch { throw new PaymentError('play_verification_unavailable'); }
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(raw));
  return {body, evidenceSha256: Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2, '0')).join('')};
}

/** purchases.subscriptions.acknowledge is required within 3 days or Google refunds. */
export async function acknowledge(config, token, productId, purchaseToken, fetcher = fetch) {
  const url = `${API}/${encodeURIComponent(config.packageName)}/purchases/subscriptions/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`;
  const response = await fetcher(url, {method: 'POST', headers: {Authorization: `Bearer ${token}`, 'Content-Type': 'application/json'},
    body: '{}', signal: AbortSignal.timeout(10_000), redirect: 'error'});
  // 400 "already acknowledged" is fine; anything else leaves the purchase to retry on next verify/RTDN.
  return response.ok || response.status === 400;
}

const STATES = {
  SUBSCRIPTION_STATE_ACTIVE: 'ACTIVE', SUBSCRIPTION_STATE_IN_GRACE_PERIOD: 'IN_GRACE_PERIOD',
  SUBSCRIPTION_STATE_CANCELED: 'CANCELED', SUBSCRIPTION_STATE_ON_HOLD: 'ON_HOLD',
  SUBSCRIPTION_STATE_PAUSED: 'PAUSED', SUBSCRIPTION_STATE_EXPIRED: 'EXPIRED',
  SUBSCRIPTION_STATE_PENDING: 'PENDING', SUBSCRIPTION_STATE_UNSPECIFIED: 'UNKNOWN',
};

/**
 * Normalize a SubscriptionPurchaseV2 into the exact arguments the ledger accepts.
 * Throws when Google's answer does not describe a usable SafeNest plan.
 */
export function normalizeSubscription(body, {packageName, basePlanToPlanCode = defaultPlanMap, now = Date.now()} = {}) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) throw new PaymentError('play_verification_unavailable');
  const stateRaw = String(body.subscriptionState ?? '');
  let state = STATES[stateRaw] ?? 'UNKNOWN';
  if (stateRaw === 'SUBSCRIPTION_STATE_EXPIRED' && body.canceledStateContext?.developerInitiatedCancellation) state = 'REVOKED';
  const pending = state === 'PENDING';
  const items = Array.isArray(body.lineItems) ? body.lineItems : [];
  if (items.length === 0) throw new PaymentError('purchase_not_found', 404);
  // Pick the line item with the latest expiry: upgrades/downgrades can carry several.
  const item = items.map(i => ({...i, expiryMs: Date.parse(i.expiryTime ?? '')}))
    .filter(i => Number.isFinite(i.expiryMs)).sort((a, b) => b.expiryMs - a.expiryMs)[0]
    ?? (pending ? items[0] : null);
  if (!item) throw new PaymentError('play_verification_unavailable');
  const productId = String(item.productId ?? '');
  const basePlanId = String(item.offerDetails?.basePlanId ?? '');
  const planCode = basePlanToPlanCode(productId, basePlanId);
  if (!PLAN_CODES.has(planCode)) throw new PaymentError('unknown_play_plan', 400);
  const startMs = Date.parse(body.startTime ?? '');
  if (!pending && (!Number.isFinite(startMs) || item.expiryMs <= startMs)) throw new PaymentError('play_verification_unavailable');
  return {
    purchaseToken: null, productId, basePlanId, planCode, state,
    // Pending-at-signup purchases have no access window yet. Both handlers return
    // before the ledger/acknowledgment path; never invent entitlement timestamps.
    startsAt: pending ? null : new Date(startMs).toISOString(), expiresAt: pending ? null : new Date(item.expiryMs).toISOString(),
    orderId: typeof body.latestOrderId === 'string' ? body.latestOrderId.slice(0, 200) : null,
    linkedPurchaseToken: typeof body.linkedPurchaseToken === 'string' ? body.linkedPurchaseToken : null,
    acknowledged: body.acknowledgementState === 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
    externalAccountId: typeof body.externalAccountIdentifiers?.obfuscatedExternalAccountId === 'string'
      ? body.externalAccountIdentifiers.obfuscatedExternalAccountId : null,
    isTestPurchase: !!body.testPurchase, now,
  };
}

/** Play Console product "safenest_protection" with base plans named after SafeNest plan codes. */
export function defaultPlanMap(productId, basePlanId) {
  if (productId !== 'safenest_protection') return '';
  return PLAN_CODES.has(basePlanId) ? basePlanId : '';
}

export function rpcArguments(userId, purchaseToken, sub, evidenceSha256) {
  return {
    p_user_id: userId, p_purchase_token: purchaseToken, p_product_id: sub.productId,
    p_base_plan_id: sub.basePlanId, p_plan_code: sub.planCode, p_state: sub.state,
    p_starts_at: sub.startsAt, p_expires_at: sub.expiresAt, p_order_id: sub.orderId,
    p_linked_purchase_token: sub.linkedPurchaseToken, p_evidence_sha256: evidenceSha256,
  };
}

/** Like serviceRpc, but surfaces the Postgres exception text (e.g. purchase_bound_to_other_account). */
export function playRpc(env, fetcher = fetch) {
  return async (name, args) => {
    let key = env.SUPABASE_SERVICE_ROLE_KEY;
    try { key = JSON.parse(env.SUPABASE_SECRET_KEYS || '{}').default || key; } catch {}
    if (!env.SUPABASE_URL || !key) throw new PaymentError('payment_backend_unavailable');
    const response = await fetcher(`${env.SUPABASE_URL}/rest/v1/rpc/${name}`, {
      method: 'POST', headers: {apikey: key, ...(key.startsWith('sb_secret_') ? {} : {Authorization: `Bearer ${key}`}),
        'Content-Type': 'application/json'},
      body: JSON.stringify(args), signal: AbortSignal.timeout(10_000), redirect: 'error',
    });
    if (!response.ok) {
      let detail = '';
      try { detail = String((await response.json())?.message ?? ''); } catch {}
      if (detail.includes('purchase_bound_to_other_account')) throw new PaymentError('purchase_bound_to_other_account', 409);
      if (detail.includes('purchase_not_bound')) throw new PaymentError('purchase_not_bound', 404);
      throw new PaymentError('payment_processing_unavailable');
    }
    return response.json();
  };
}
