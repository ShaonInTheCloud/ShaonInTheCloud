import {PaymentError, configuredAdapter, processNotification, readLimitedBody, serviceRpc} from '../_shared/payments.mjs';

export async function handleNotification(req, env, {fetcher=fetch,rpc=serviceRpc(env,fetcher),
  adapterFor=()=>configuredAdapter(env,rpc,fetcher)} = {}) {
  const json = (body, status) => new Response(JSON.stringify(body), {status,
    headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
  if (req.method !== 'POST') return json({error:'method_not_allowed'},405);
  try {
    // A public gateway callback has no customer JWT. The compiled server resolver
    // fixes merchant/provider/environment; no untrusted routing or test mode flag.
    const adapter = adapterFor();
    const raw = await readLimitedBody(req);
    await processNotification(adapter, raw, req.headers, rpc);
    // No customer, transaction, entitlement or validation reference in the ACK.
    return json({received:true},200);
  } catch (error) {
    return json({error:error instanceof PaymentError ? error.code : 'payment_verification_unavailable'},
      error instanceof PaymentError ? error.status : 503);
  }
}
