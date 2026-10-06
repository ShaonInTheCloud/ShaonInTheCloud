import {PaymentError, configuredAdapter, checkoutUrl, readLimitedBody, serviceRpc, uuid} from '../_shared/payments.mjs';

export async function handleOrder(req, env, {fetcher = fetch, adapterFor = configuredAdapter,
  rpc = serviceRpc(env, fetcher)} = {}) {
  const origin = req.headers.get('origin');
  const allowed = new Set(['https://mysafenestbd.com']);
  const cors = origin && allowed.has(origin) ? {'Access-Control-Allow-Origin':origin,
    'Access-Control-Allow-Headers':'authorization,apikey,content-type',
    'Access-Control-Allow-Methods':'POST,OPTIONS', Vary:'Origin'} : {};
  const json = (body, status = 200) => new Response(JSON.stringify(body), {status,
    headers:{...cors, 'Content-Type':'application/json', 'Cache-Control':'no-store'}});
  if (origin && !allowed.has(origin)) return json({error:'origin_not_allowed'},403);
  if (req.method === 'OPTIONS') return new Response(null, {status:204,headers:cors});
  if (req.method !== 'POST') return json({error:'method_not_allowed'},405);
  try {
    const authorization = req.headers.get('authorization') ?? '';
    if (!/^Bearer [A-Za-z0-9_.-]+$/.test(authorization)) return json({error:'authentication_required'},401);
    if (!env.SUPABASE_URL || !env.SUPABASE_ANON_KEY) return json({error:'payment_backend_unavailable'},503);
    const auth = await fetcher(`${env.SUPABASE_URL}/auth/v1/user`, {headers:{
      apikey:env.SUPABASE_ANON_KEY, Authorization:authorization}, signal:AbortSignal.timeout(10_000),redirect:'error'});
    if (!auth.ok) return json({error:'authentication_required'},401);
    const user = await auth.json();
    if (!uuid.test(user.id ?? '') || user.is_anonymous === true || !user.email_confirmed_at)
      return json({error:'confirmed_account_required'},403);
    const raw = await readLimitedBody(req);
    let body;
    try { body = JSON.parse(raw); } catch { throw new PaymentError('invalid_request',400); }
    if (!body || typeof body !== 'object' || Array.isArray(body) ||
      Object.keys(body).some(key => !['product_code','idempotency_key'].includes(key)) ||
      typeof body.product_code !== 'string' || !/^[a-z][a-z0-9_-]{0,79}$/.test(body.product_code) ||
      typeof body.idempotency_key !== 'string' || !/^[A-Za-z0-9_-]{16,128}$/.test(body.idempotency_key))
      throw new PaymentError('invalid_request',400);
    // Server selects provider/environment. Buyers cannot set price, duration, identity,
    // mode or receipt evidence. The production resolver always rejects for now.
    const adapter = adapterFor();
    const order = await rpc('create_payment_order', {p_user_id:user.id,p_product_code:body.product_code,
      p_provider:adapter.provider,p_environment:adapter.environment,p_idempotency_key:body.idempotency_key});
    if (order.state !== 'pending' || Date.parse(order.expires_at) <= Date.now())
      throw new PaymentError('order_not_payable',409);
    // Adapter must reuse order.id as its provider session idempotency key. Session
    // failure leaves a retryable pending order and never issues an entitlement.
    const session = await adapter.createHostedSession(order);
    return json({order_id:order.id,checkout_url:checkoutUrl(adapter,session.url)});
  } catch (error) {
    return json({error:error instanceof PaymentError ? error.code : 'payment_backend_unavailable'},
      error instanceof PaymentError ? error.status : 503);
  }
}
