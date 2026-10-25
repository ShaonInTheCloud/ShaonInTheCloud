import {PaymentError, readLimitedBody} from '../_shared/payments.mjs';
import {PURCHASE_TOKEN, accessToken, acknowledge, fetchSubscription, normalizeSubscription, playConfig, playRpc, rpcArguments} from '../_shared/google-play.mjs';

/**
 * Authenticated: the signed-in SafeNest account sends the purchase token it just
 * bought in the Play app. The server re-fetches the subscription from Google,
 * binds the token to this account, acknowledges it and issues the entitlement.
 * Reply shape matches protection-access so the app reuses its parser.
 */
export async function handleVerify(req, env, {fetcher = fetch, rpc = playRpc(env, fetcher), now = () => new Date()} = {}) {
  const json = (body, status = 200) => new Response(JSON.stringify(body), {status,
    headers: {'Content-Type': 'application/json', 'Cache-Control': 'no-store'}});
  if (req.method !== 'POST') return json({error: 'method_not_allowed'}, 405);
  const authorization = req.headers.get('Authorization') ?? '';
  if (!/^Bearer [A-Za-z0-9_.-]+$/.test(authorization)) return json({error: 'authentication_required'}, 401);
  if (!env.SUPABASE_URL || !env.SUPABASE_ANON_KEY) return json({error: 'not_configured'}, 503);
  try {
    const auth = await fetcher(`${env.SUPABASE_URL}/auth/v1/user`, {headers: {apikey: env.SUPABASE_ANON_KEY, Authorization: authorization},
      redirect: 'error', signal: AbortSignal.timeout(10_000)});
    if (!auth.ok) return json({error: 'authentication_required'}, 401);
    const user = await auth.json();
    if (!user?.id || user.is_anonymous === true) return json({error: 'account_required'}, 403);
    let body;
    try { body = JSON.parse((await readLimitedBody(req)) || '{}'); } catch { return json({error: 'invalid_request'}, 400); }
    if (!body || typeof body !== 'object' || Array.isArray(body)) return json({error: 'invalid_request'}, 400);
    const purchaseToken = body.purchase_token;
    if (typeof purchaseToken !== 'string' || !PURCHASE_TOKEN.test(purchaseToken)) return json({error: 'invalid_purchase_token'}, 400);

    const config = playConfig(env);
    const token = await accessToken(config, fetcher);
    const {body: google, evidenceSha256} = await fetchSubscription(config, token, purchaseToken, fetcher);
    const sub = normalizeSubscription(google, {packageName: config.packageName});
    if (sub.isTestPurchase && !/^(true|1)$/i.test(env.PLAY_ALLOW_TEST_PURCHASES ?? '')) return json({error: 'test_purchase_not_allowed'}, 403);
    // The app sets obfuscatedAccountId to the SafeNest user id at purchase time; a mismatch is another account's token.
    if (sub.externalAccountId && sub.externalAccountId !== user.id) return json({error: 'purchase_bound_to_other_account'}, 409);
    if (sub.state === 'PENDING') return json({active: false, pending: true, user_id: user.id, server_now: now().toISOString(), checked_entitlement_id: null, entitlement: null});

    const result = await rpc('process_play_subscription', rpcArguments(user.id, purchaseToken, sub, evidenceSha256));
    if (!sub.acknowledged && ['ACTIVE', 'IN_GRACE_PERIOD', 'CANCELED'].includes(sub.state)) {
      await acknowledge(config, token, sub.productId, purchaseToken, fetcher);
    }
    const active = result.status === 'active';
    return json({active, user_id: user.id, server_now: now().toISOString(), checked_entitlement_id: null,
      entitlement: active ? {id: result.entitlement_id, plan_code: result.plan_code, starts_at: result.starts_at, ends_at: result.ends_at} : null});
  } catch (error) {
    if (error instanceof PaymentError) return json({error: error.code}, error.status);
    return json({error: 'play_verification_unavailable'}, 503);
  }
}
