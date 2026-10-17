import {PaymentError, readLimitedBody} from './payments.mjs';
import {constantTimeEqual} from './stripe.mjs';

/**
 * NOWPayments adapter (hosted cryptocurrency checkout; the merchant's payout wallet —
 * for SafeNest, the owner's Bybit USDT deposit address — is configured in the
 * NOWPayments dashboard, never in code). Disabled until every variable is set:
 *
 *   NOWPAYMENTS_ENABLED=true          NOWPAYMENTS_ENVIRONMENT=sandbox|live
 *   NOWPAYMENTS_API_KEY=…             NOWPAYMENTS_IPN_SECRET=…
 *   NOWPAYMENTS_LIVE_APPROVED=true    (live only)
 *   NOWPAYMENTS_CHECKOUT_ENABLED=true
 *
 * Evidence model: the IPN signature (HMAC-SHA512 over the key-sorted JSON) is checked, then
 * the payment is fetched again from the NOWPayments API with the merchant key. Only a
 * payment reported as `finished` for the exact order, fiat amount and currency is a receipt.
 */
const token=(v,max=200)=>typeof v==='string' && /^[A-Za-z0-9_-]+$/.test(v) && v.length<=max;
const encoder=new TextEncoder();
const hex=buffer=>Array.from(new Uint8Array(buffer),b=>b.toString(16).padStart(2,'0')).join('');
async function digest(value) {return hex(await crypto.subtle.digest('SHA-256',encoder.encode(JSON.stringify(value))));}
export function sortedJson(value) {
  if (Array.isArray(value)) return '['+value.map(sortedJson).join(',')+']';
  if (value && typeof value==='object')
    return '{'+Object.keys(value).sort().map(k=>JSON.stringify(k)+':'+sortedJson(value[k])).join(',')+'}';
  return JSON.stringify(value);
}
export async function ipnSignature(secret,payload) {
  const key=await crypto.subtle.importKey('raw',encoder.encode(secret),{name:'HMAC',hash:'SHA-512'},false,['sign']);
  return hex(await crypto.subtle.sign('HMAC',key,encoder.encode(sortedJson(payload))));
}
/** Fiat price as reported by NOWPayments (number or numeric string) → minor units. */
export function fiatMinor(value) {
  const text=typeof value==='number'?value.toFixed(2):String(value??'');
  if (!/^(0|[1-9][0-9]{0,7})(\.[0-9]{1,8})?$/.test(text)) throw new PaymentError('invalid_provider_amount',400);
  const [whole,fraction='']=text.split('.');
  if (fraction.length>2 && Number(fraction.slice(2))!==0) throw new PaymentError('invalid_provider_amount',400);
  const result=Number(whole)*100+Number(fraction.slice(0,2).padEnd(2,'0'));
  if (!Number.isSafeInteger(result) || result<=0) throw new PaymentError('invalid_provider_amount',400);
  return result;
}
function providerTime(value) {
  const time=Date.parse(typeof value==='string'?value:'');
  if (!Number.isFinite(time)) throw new PaymentError('invalid_provider_time',400);
  return new Date(time).toISOString();
}
const finalStates={finished:'paid',failed:'failed',expired:'cancelled'};
const planNames={weekly:'weekly',monthly:'monthly',quarterly:'three-month',annual:'annual'};

export function nowpaymentsAdapter(env,rpc,fetcher=fetch) {
  if (env.NOWPAYMENTS_ENABLED!=='true') throw new PaymentError('payment_provider_not_configured');
  const environment=env.NOWPAYMENTS_ENVIRONMENT;
  const apiKey=env.NOWPAYMENTS_API_KEY,ipnSecret=env.NOWPAYMENTS_IPN_SECRET;
  if (!['sandbox','live'].includes(environment) || (environment==='live' && env.NOWPAYMENTS_LIVE_APPROVED!=='true') ||
      typeof apiKey!=='string' || !/^[A-Za-z0-9_-]{10,200}$/.test(apiKey) ||
      typeof ipnSecret!=='string' || ipnSecret.length<16 || ipnSecret.length>200 || !env.SUPABASE_URL)
    throw new PaymentError('payment_provider_not_configured');
  const base=environment==='live'?'https://api.nowpayments.io':'https://api-sandbox.nowpayments.io';
  const checkoutOrigin=environment==='live'?'https://nowpayments.io':'https://sandbox.nowpayments.io';
  const callback=new URL('/functions/v1/payment-return',env.SUPABASE_URL);
  callback.searchParams.set('provider','nowpayments');
  const ipn=new URL('/functions/v1/payment-notification',env.SUPABASE_URL);
  ipn.searchParams.set('provider','nowpayments');
  async function api(path,body) {
    const response=await fetcher(base+path,{method:body?'POST':'GET',redirect:'error',signal:AbortSignal.timeout(15000),
      headers:{'x-api-key':apiKey,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined});
    const text=await readLimitedBody(response,65536);
    let obj;
    try {obj=JSON.parse(text);if (!obj || Array.isArray(obj) || typeof obj!=='object') throw 0;}
    catch {throw new PaymentError('invalid_provider_response');}
    if (!response.ok) throw new PaymentError(response.status===404?'payment_requires_review':'payment_provider_unavailable',response.status===404?409:503);
    return obj;
  }
  async function normalize(order,paymentId) {
    if (!token(paymentId)) throw new PaymentError('invalid_notification',400);
    const payment=await api(`/v1/payment/${paymentId}`);
    if (String(payment.payment_id)!==paymentId || payment.order_id!==order.provider_order_id ||
      String(payment.price_currency??'').toUpperCase()!==order.currency || fiatMinor(payment.price_amount)!==Number(order.amount_minor))
      throw new PaymentError('payment_order_mismatch',400);
    try {await rpc('payment_session_note',{p_order_id:order.id,p_payment_id:paymentId});}
    catch {throw new PaymentError('payment_order_mismatch',400);}
    const status=finalStates[payment.payment_status];
    if (payment.payment_status==='partially_paid' || payment.payment_status==='refunded')
      throw new PaymentError('payment_requires_review',409);
    if (!status) throw new PaymentError('event_ignored',200); // waiting / confirming / sending: not final yet.
    if (status!=='paid') return {orderId:order.id,provider:'nowpayments',environment,status,
      amountMinor:Number(order.amount_minor),currency:order.currency,eventId:`${status}:${paymentId}`,
      validationReference:paymentId,evidenceSha256:await digest({orderId:order.id,status,paymentId})};
    const proof={orderId:order.id,provider:'nowpayments',environment,status:'paid',amountMinor:Number(order.amount_minor),
      currency:order.currency,transactionId:paymentId,paidAt:providerTime(payment.updated_at),validationReference:paymentId};
    return {...proof,eventId:'paid:'+paymentId,evidenceSha256:await digest(proof)};
  }
  return {
    provider:'nowpayments',environment,checkoutOrigins:[checkoutOrigin],
    checkoutOpen:env.NOWPAYMENTS_CHECKOUT_ENABLED==='true',
    async createHostedSession(order,customer) {
      if (env.NOWPAYMENTS_CHECKOUT_ENABLED!=='true') throw new PaymentError('checkout_not_open');
      if (order.provider!=='nowpayments' || order.environment!==environment || !/^[A-Z]{3}$/.test(order.currency??'') ||
        !Number.isSafeInteger(Number(order.amount_minor)) || Number(order.amount_minor)<100 ||
        Number(order.amount_minor)>99999999 || !token(order.provider_order_id,30))
        throw new PaymentError('invalid_provider_order',400);
      if (!customer || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customer.email??'') || customer.email.length>254)
        throw new PaymentError('billing_details_required',400);
      const claim=await rpc('payment_session_claim',{p_order_id:order.id});
      if (claim.state==='ready') return {url:claim.checkout_url};
      if (claim.state!=='claimed') throw new PaymentError('checkout_session_pending_review',409);
      try {
        const result=await api('/v1/invoice',{price_amount:Number(order.amount_minor)/100,price_currency:order.currency.toLowerCase(),
          order_id:order.provider_order_id,order_description:`SafeNest protection — ${planNames[order.plan_code]??order.plan_code} plan`,
          ipn_callback_url:ipn.href,success_url:callback.href,cancel_url:callback.href,is_fixed_rate:true,is_fee_paid_by_user:true});
        const invoiceId=String(result.id??'');
        if (!token(invoiceId)) throw new PaymentError('checkout_session_unavailable');
        let url;try {url=new URL(result.invoice_url);}catch {throw new PaymentError('invalid_provider_checkout');}
        if (url.origin!==checkoutOrigin || url.pathname!=='/payment/' || url.username || url.password) throw new PaymentError('invalid_provider_checkout');
        await rpc('payment_session_save',{p_order_id:order.id,p_session_id:invoiceId,p_url:url.href});
        return {url:url.href};
      } catch {
        try {await rpc('payment_session_review',{p_order_id:order.id});}catch {}
        throw new PaymentError('checkout_session_pending_review',409);
      }
    },
    async validateNotification(raw,headers) {
      const signature=headers.get('x-nowpayments-sig')??'';
      if (!/^[0-9a-f]{128}$/.test(signature) || !(headers.get('content-type')??'').toLowerCase().startsWith('application/json'))
        throw new PaymentError('invalid_notification',400);
      let payload;
      try {payload=JSON.parse(raw);if (!payload || Array.isArray(payload) || typeof payload!=='object') throw 0;}
      catch {throw new PaymentError('invalid_notification',400);}
      if (!constantTimeEqual(signature,await ipnSignature(ipnSecret,payload))) throw new PaymentError('invalid_notification',400);
      const paymentId=String(payload.payment_id??'');
      if (!token(paymentId) || !token(payload.order_id,30)) throw new PaymentError('invalid_notification',400);
      const order=await rpc('payment_order_lookup',{p_reference:payload.order_id,p_provider:'nowpayments',p_environment:environment});
      if (!order) throw new PaymentError('order_not_found',404);
      return normalize(order,paymentId);
    },
    async reconcile(order) {
      if (order.provider!=='nowpayments' || order.environment!==environment) throw new PaymentError('reconciliation_order_mismatch',400);
      // Without a provider payment id (no IPN ever arrived) the dashboard is the only source of truth.
      if (!token(order.provider_payment_id)) throw new PaymentError('payment_requires_review',409);
      return normalize(order,order.provider_payment_id);
    },
  };
}
