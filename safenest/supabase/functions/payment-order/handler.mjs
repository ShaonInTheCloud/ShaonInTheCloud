import {PaymentError, configuredAdapter, checkoutUrl, readLimitedBody, serviceRpc, uuid} from '../_shared/payments.mjs';

export async function handleOrder(req, env, {fetcher = fetch, rpc = serviceRpc(env, fetcher),
  adapterFor = () => configuredAdapter(env,rpc,fetcher)} = {}) {
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
    let publicKey=env.SUPABASE_ANON_KEY;
    try {publicKey=JSON.parse(env.SUPABASE_PUBLISHABLE_KEYS || '{}').default || publicKey;} catch {}
    if (!env.SUPABASE_URL || !publicKey) return json({error:'payment_backend_unavailable'},503);
    const auth = await fetcher(`${env.SUPABASE_URL}/auth/v1/user`, {headers:{
      apikey:publicKey, Authorization:authorization}, signal:AbortSignal.timeout(10_000),redirect:'error'});
    if (!auth.ok) return json({error:'authentication_required'},401);
    const user = await auth.json();
    if (!uuid.test(user.id ?? '') || user.is_anonymous === true || !user.email_confirmed_at)
      return json({error:'confirmed_account_required'},403);
    const raw = await readLimitedBody(req);
    let body;
    try { body = JSON.parse(raw); } catch { throw new PaymentError('invalid_request',400); }
    if (!body || typeof body !== 'object' || Array.isArray(body) ||
      Object.keys(body).some(key => !['product_code','idempotency_key','customer'].includes(key)) ||
      typeof body.product_code !== 'string' || !/^[a-z][a-z0-9_-]{0,79}$/.test(body.product_code) ||
      typeof body.idempotency_key !== 'string' || !/^[A-Za-z0-9_-]{16,128}$/.test(body.idempotency_key))
      throw new PaymentError('invalid_request',400);
    // Server selects provider/environment. Buyers cannot set price, duration, identity,
    // mode or receipt evidence. The resolver is disabled until operator configuration.
    const adapter = adapterFor();
    const order = await rpc('create_payment_order', {p_user_id:user.id,p_product_code:body.product_code,
      p_provider:adapter.provider,p_environment:adapter.environment,p_idempotency_key:body.idempotency_key});
    if (order.state !== 'pending' || Date.parse(order.expires_at) <= Date.now())
      throw new PaymentError('order_not_payable',409);
    // Adapter reuses the saved session; uncertain creation requires reconciliation.
    const customer=body.customer && typeof body.customer==='object' && !Array.isArray(body.customer)
      ? {...body.customer,email:user.email} : undefined;
    const session = await adapter.createHostedSession(order,customer);
    return json({order_id:order.id,checkout_url:checkoutUrl(adapter,session.url)});
  } catch (error) {
    return json({error:error instanceof PaymentError ? error.code : 'payment_backend_unavailable'},
      error instanceof PaymentError ? error.status : 503);
  }
}
