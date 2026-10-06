import {sslcommerzAdapter} from './sslcommerz.mjs';
/**
 * Payment orchestration with a disabled-by-default SSLCOMMERZ adapter.
 * A concrete adapter MUST validate notifications independently with the provider,
 * verify merchant/account and environment, and normalize the provider response.
 * Browser redirects and client fields are never validation evidence.
 */
export class PaymentError extends Error {
  constructor(code, status = 503) { super(code); this.code = code; this.status = status; }
}
export const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const statuses = new Set(['pending', 'paid', 'failed', 'cancelled', 'refunded', 'chargeback']);
const boundedText = value => typeof value === 'string' && value.length > 0 && value.length <= 200;

/** @typedef {{provider:string, environment:'sandbox'|'live',
 * createHostedSession:(order:object)=>Promise<{url:string}>,
 * validateNotification:(raw:string, headers:Headers)=>Promise<object>,
 * reconcile:(order:object)=>Promise<object>, checkoutOrigins:ReadonlyArray<string>}} ProviderAdapter
 */
// Merchant credentials, environment and explicit approval gates are server-only.
export function configuredAdapter(env = {}, rpc, fetcher = fetch) {
  return sslcommerzAdapter(env,rpc,fetcher);
}

export function checkoutUrl(adapter, value) {
  let url;
  try { url = new URL(value); } catch { throw new PaymentError('invalid_provider_checkout'); }
  if (url.protocol !== 'https:' || url.username || url.password ||
      !adapter.checkoutOrigins.includes(url.origin)) throw new PaymentError('invalid_provider_checkout');
  return url.href;
}

/** Only call on independent provider-validation output, never raw webhook JSON. */
export function validatedArguments(adapter, evidence) {
  if (!/^[a-z][a-z0-9_-]{0,39}$/.test(adapter.provider ?? '') ||
      !['sandbox','live'].includes(adapter.environment) ||
      !evidence || typeof evidence !== 'object' || Array.isArray(evidence) ||
      evidence.provider !== adapter.provider || evidence.environment !== adapter.environment ||
      !uuid.test(evidence.orderId ?? '') || !boundedText(evidence.eventId) ||
      !boundedText(evidence.validationReference) || !statuses.has(evidence.status) ||
      !Number.isSafeInteger(evidence.amountMinor) || evidence.amountMinor <= 0 ||
      !/^[A-Z]{3}$/.test(evidence.currency ?? '') ||
      !/^[0-9a-f]{64}$/.test(evidence.evidenceSha256 ?? '')) {
    throw new PaymentError('invalid_provider_evidence', 400);
  }
  const settled = ['paid', 'refunded', 'chargeback'].includes(evidence.status);
  if ((settled && (!boundedText(evidence.transactionId) ||
      typeof evidence.paidAt !== 'string' || !Number.isFinite(Date.parse(evidence.paidAt)))) ||
      (!settled && (evidence.transactionId != null || evidence.paidAt != null))) {
    throw new PaymentError('invalid_provider_evidence', 400);
  }
  // Whitelist persisted evidence; no raw payload, card fields, email or arbitrary metadata.
  return {
    p_order_id: evidence.orderId, p_provider: adapter.provider, p_environment: adapter.environment,
    p_event_id: evidence.eventId, p_transaction_id: evidence.transactionId ?? null,
    p_status: evidence.status, p_amount_minor: evidence.amountMinor, p_currency: evidence.currency,
    p_paid_at: settled ? new Date(evidence.paidAt).toISOString() : null,
    p_validation_reference: evidence.validationReference, p_evidence_sha256: evidence.evidenceSha256,
  };
}

export async function processNotification(adapter, raw, headers, rpc) {
  // The adapter must use a fixed provider API endpoint/merchant identity, with bounded
  // timeouts and no redirects to notification-supplied URLs. This call must complete
  // before any ledger operation. Unknown/partial reversals require manual review.
  const evidence = await adapter.validateNotification(raw, headers);
  return rpc('process_validated_payment', validatedArguments(adapter, evidence));
}

export async function reconcileOrder(adapter, order, rpc) {
  const evidence = await adapter.reconcile(order);
  const args = validatedArguments(adapter, evidence);
  if (args.p_order_id !== order.id || adapter.provider !== order.provider ||
      adapter.environment !== order.environment) throw new PaymentError('reconciliation_order_mismatch',400);
  return rpc('process_validated_payment', args);
}

export async function readLimitedBody(req, limit = 16_384) {
  if (Number(req.headers.get('content-length')) > limit) throw new PaymentError('invalid_request', 413);
  if (!req.body) return '';
  const reader = req.body.getReader();
  const chunks = []; let size = 0;
  while (true) {
    const {done, value} = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > limit) { await reader.cancel(); throw new PaymentError('invalid_request', 413); }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  try { return new TextDecoder('utf-8', {fatal: true}).decode(bytes); }
  catch { throw new PaymentError('invalid_request',400); }
}

export function serviceRpc(env, fetcher = fetch) {
  return async (name, args) => {
    let key=env.SUPABASE_SERVICE_ROLE_KEY;
    try {key=JSON.parse(env.SUPABASE_SECRET_KEYS || '{}').default || key;} catch {}
    if (!env.SUPABASE_URL || !key) throw new PaymentError('payment_backend_unavailable');
    const response = await fetcher(`${env.SUPABASE_URL}/rest/v1/rpc/${name}`, {
      method: 'POST', headers: {apikey:key,...(key.startsWith('sb_secret_')?{}:{Authorization:`Bearer ${key}`}),
        'Content-Type': 'application/json'},
      body: JSON.stringify(args), signal: AbortSignal.timeout(10_000), redirect: 'error',
    });
    if (!response.ok) throw new PaymentError('payment_processing_unavailable');
    return response.json();
  };
}
