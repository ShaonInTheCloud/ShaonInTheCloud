import {PaymentError, readLimitedBody} from '../_shared/payments.mjs';
import {PURCHASE_TOKEN, accessToken, acknowledge, fetchSubscription, normalizeSubscription, playConfig, playRpc, rpcArguments} from '../_shared/google-play.mjs';

/**
 * Google Play Real-time Developer Notifications arrive as Pub/Sub push messages.
 * The push URL carries ?key=<PLAY_RTDN_SECRET>; without it the request is rejected.
 * The notification body is only a hint: the server re-fetches the subscription
 * from Google and updates the ledger/entitlement for the already-bound account.
 */
function sameSecret(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string' || a.length !== b.length || a.length < 32) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

export async function handleRtdn(req, env, {fetcher = fetch, rpc = playRpc(env, fetcher)} = {}) {
  const json = (body, status = 200) => new Response(JSON.stringify(body), {status,
    headers: {'Content-Type': 'application/json', 'Cache-Control': 'no-store'}});
  if (req.method !== 'POST') return json({error: 'method_not_allowed'}, 405);
  const key = new URL(req.url).searchParams.get('key') ?? '';
  if (!sameSecret(key, env.PLAY_RTDN_SECRET ?? '')) return json({error: 'forbidden'}, 403);
  try {
    let envelope;
    try { envelope = JSON.parse((await readLimitedBody(req, 65_536)) || '{}'); } catch { return json({error: 'invalid_request'}, 400); }
    const data = envelope?.message?.data;
    if (typeof data !== 'string') return json({error: 'invalid_request'}, 400);
    let notification;
    try { notification = JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(data), c => c.charCodeAt(0)))); }
    catch { return json({error: 'invalid_request'}, 400); }
    const config = playConfig(env);
    if (notification?.packageName && notification.packageName !== config.packageName) return json({ignored: 'other_package'}, 200);
    // Test notifications and one-time-product notifications carry nothing to apply.
    const purchaseToken = notification?.subscriptionNotification?.purchaseToken;
    if (typeof purchaseToken !== 'string') return json({ignored: 'not_subscription'}, 200);
    if (!PURCHASE_TOKEN.test(purchaseToken)) return json({error: 'invalid_purchase_token'}, 400);

    const token = await accessToken(config, fetcher);
    const {body, evidenceSha256} = await fetchSubscription(config, token, purchaseToken, fetcher);
    const sub = normalizeSubscription(body, {packageName: config.packageName});
    if (sub.state === 'PENDING') return json({received: true, pending: true}, 200);
    // p_user_id null: the ledger must already know this token (bound by play-billing-verify).
    const result = await rpc('process_play_subscription', rpcArguments(null, purchaseToken, sub, evidenceSha256));
    if (!sub.acknowledged && ['ACTIVE', 'IN_GRACE_PERIOD', 'CANCELED'].includes(sub.state)) {
      await acknowledge(config, token, sub.productId, purchaseToken, fetcher);
    }
    return json({received: true, status: result.status}, 200);
  } catch (error) {
    if (error instanceof PaymentError) {
      // A 2xx tells Pub/Sub to stop retrying; unbound tokens are expected before the app verifies.
      if (error.code === 'purchase_not_bound' || error.code === 'purchase_not_found') return json({received: true, ignored: error.code}, 200);
      return json({error: error.code}, error.status);
    }
    return json({error: 'play_verification_unavailable'}, 503);
  }
}
