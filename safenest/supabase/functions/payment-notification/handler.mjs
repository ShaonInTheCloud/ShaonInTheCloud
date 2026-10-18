import {PaymentError, configuredAdapter, notificationProvider, processNotification, readLimitedBody, serviceRpc} from '../_shared/payments.mjs';

export async function handleNotification(req, env, {fetcher=fetch,rpc=serviceRpc(env,fetcher),
  adapterFor=provider=>configuredAdapter(env,rpc,fetcher,provider)} = {}) {
  const json = (body, status) => new Response(JSON.stringify(body), {status,
    headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
  if (req.method !== 'POST') return json({error:'method_not_allowed'},405);
  try {
    // A public gateway callback has no customer JWT. The provider is chosen only among
    // adapters configured server-side (by its signature header or ?provider=); the
    // callback can never pick merchant credentials, environment or a test mode.
    const adapter = adapterFor(notificationProvider(req));
    const raw = await readLimitedBody(req);
    await processNotification(adapter, raw, req.headers, rpc);
    // No customer, transaction, entitlement or validation reference in the ACK.
    return json({received:true},200);
  } catch (error) {
    // event_ignored (200) acknowledges an unrelated or not-yet-final event so the gateway stops retrying.
    return json({error:error instanceof PaymentError ? error.code : 'payment_verification_unavailable'},
      error instanceof PaymentError ? error.status : 503);
  }
}
