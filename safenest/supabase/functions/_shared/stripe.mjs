import {PaymentError, readLimitedBody} from './payments.mjs';

/**
 * Stripe Checkout adapter (hosted card payments: Mastercard, Visa and whatever the
 * Stripe account enables). Disabled until the operator sets every variable below.
 *
 *   STRIPE_ENABLED=true            STRIPE_ENVIRONMENT=sandbox|live
 *   STRIPE_SECRET_KEY=sk_test_…|sk_live_…   STRIPE_WEBHOOK_SECRET=whsec_…
 *   STRIPE_LIVE_APPROVED=true      (live only; the explicit go-live gate)
 *   STRIPE_CHECKOUT_ENABLED=true   (opens the catalogue/order path for this provider)
 *
 * Evidence model: a webhook is only a hint. The signature is verified, then the Checkout
 * Session is fetched again from api.stripe.com with the merchant key, and the paid
 * charge inside it is the receipt. Nothing from the webhook body enters the ledger.
 */
const token=(v,max=200)=>typeof v==='string' && /^[A-Za-z0-9_-]+$/.test(v) && v.length<=max;
const encoder=new TextEncoder();
async function digest(value) {
  const data=await crypto.subtle.digest('SHA-256',encoder.encode(JSON.stringify(value)));
  return hex(data);
}
const hex=buffer=>Array.from(new Uint8Array(buffer),b=>b.toString(16).padStart(2,'0')).join('');
async function hmacSha256(secret,message) {
  const key=await crypto.subtle.importKey('raw',encoder.encode(secret),{name:'HMAC',hash:'SHA-256'},false,['sign']);
  return hex(await crypto.subtle.sign('HMAC',key,encoder.encode(message)));
}
export function constantTimeEqual(a,b) {
  if (typeof a!=='string' || typeof b!=='string' || a.length!==b.length) return false;
  let difference=0;
  for (let i=0;i<a.length;i++) difference|=a.charCodeAt(i)^b.charCodeAt(i);
  return difference===0;
}
/** Verifies a `Stripe-Signature` header against the raw body. Exported for tests. */
export async function verifyStripeSignature(header,raw,secret,now=Date.now(),toleranceSeconds=300) {
  if (typeof header!=='string' || header.length>2000) throw new PaymentError('invalid_notification',400);
  const parts=Object.create(null);
  for (const piece of header.split(',')) {
    const [k,v]=piece.split('=');
    if (!k || v===undefined) continue;
    (parts[k.trim()] ??= []).push(v.trim());
  }
  const timestamp=Number(parts.t?.[0]);
  if (!Number.isSafeInteger(timestamp) || Math.abs(now/1000-timestamp)>toleranceSeconds)
    throw new PaymentError('invalid_notification',400);
  const expected=await hmacSha256(secret,`${timestamp}.${raw}`);
  if (!(parts.v1 ?? []).some(candidate=>constantTimeEqual(candidate,expected)))
    throw new PaymentError('invalid_notification',400);
  return timestamp;
}
function form(fields,prefix='',out=new URLSearchParams()) {
  for (const [k,v] of Object.entries(fields)) {
    const name=prefix?`${prefix}[${k}]`:k;
    if (v && typeof v==='object') form(v,name,out); else if (v!==undefined) out.append(name,String(v));
  }
  return out;
}
const planNames={weekly:'weekly',monthly:'monthly',quarterly:'three-month',annual:'annual'};

export function stripeAdapter(env,rpc,fetcher=fetch) {
  if (env.STRIPE_ENABLED!=='true') throw new PaymentError('payment_provider_not_configured');
  const environment=env.STRIPE_ENVIRONMENT;
  const secret=env.STRIPE_SECRET_KEY,webhookSecret=env.STRIPE_WEBHOOK_SECRET;
  if (!['sandbox','live'].includes(environment) || (environment==='live' && env.STRIPE_LIVE_APPROVED!=='true') ||
      typeof secret!=='string' || !/^sk_(test|live)_[A-Za-z0-9]{10,}$/.test(secret) ||
      (environment==='live')!==secret.startsWith('sk_live_') ||
      typeof webhookSecret!=='string' || !/^whsec_[A-Za-z0-9]{10,}$/.test(webhookSecret) || !env.SUPABASE_URL)
    throw new PaymentError('payment_provider_not_configured');
  const checkoutOrigin='https://checkout.stripe.com';
  const callback=new URL('/functions/v1/payment-return',env.SUPABASE_URL);
  callback.searchParams.set('provider','stripe');
  async function api(path,fields,{method='GET',idempotencyKey}={}) {
    const headers={Authorization:`Bearer ${secret}`,'Stripe-Version':'2024-06-20'};
    if (idempotencyKey) headers['Idempotency-Key']=idempotencyKey;
    let url='https://api.stripe.com'+path,body;
    if (method==='GET' && fields) url+='?'+form(fields);
    if (method==='POST') {headers['Content-Type']='application/x-www-form-urlencoded';body=form(fields).toString();}
    const response=await fetcher(url,{method,headers,body,redirect:'error',signal:AbortSignal.timeout(15000)});
    const text=await readLimitedBody(response,262144);
    let obj;
    try {obj=JSON.parse(text);if (!obj || Array.isArray(obj) || typeof obj!=='object') throw 0;}
    catch {throw new PaymentError('invalid_provider_response');}
    if (!response.ok) throw new PaymentError(response.status===404?'payment_requires_review':'payment_provider_unavailable',response.status===404?409:503);
    return obj;
  }
  /** Fetch the session from Stripe and turn its paid charge into ledger evidence. */
  async function normalize(order,sessionId) {
    if (!token(sessionId)) throw new PaymentError('invalid_notification',400);
    const session=await api(`/v1/checkout/sessions/${sessionId}`,{expand:{0:'payment_intent.latest_charge'}});
    if (session.object!=='checkout.session' || session.id!==sessionId || session.livemode!==(environment==='live') ||
      session.client_reference_id!==order.provider_order_id || session.mode!=='payment')
      throw new PaymentError('payment_order_mismatch',400);
    if (session.status==='expired') return {orderId:order.id,provider:'stripe',environment,status:'cancelled',
      amountMinor:Number(order.amount_minor),currency:order.currency,eventId:'expired:'+session.id,
      validationReference:session.id,evidenceSha256:await digest({orderId:order.id,expired:session.id})};
    if (session.payment_status!=='paid') throw new PaymentError('payment_not_validated',409);
    const charge=session.payment_intent?.latest_charge;
    if (!charge || typeof charge!=='object' || charge.object!=='charge' || !token(charge.id) ||
      charge.status!=='succeeded' || charge.paid!==true || charge.refunded===true || charge.disputed===true ||
      !Number.isSafeInteger(charge.created) || !Number.isSafeInteger(session.amount_total))
      throw new PaymentError('payment_requires_review',409);
    if (session.amount_total!==Number(order.amount_minor) || charge.amount!==Number(order.amount_minor) ||
      String(session.currency).toUpperCase()!==order.currency || String(charge.currency).toUpperCase()!==order.currency)
      throw new PaymentError('payment_order_mismatch',400);
    const proof={orderId:order.id,provider:'stripe',environment,status:'paid',amountMinor:Number(order.amount_minor),
      currency:order.currency,transactionId:charge.id,paidAt:new Date(charge.created*1000).toISOString(),
      validationReference:session.id};
    return {...proof,eventId:'paid:'+charge.id,evidenceSha256:await digest(proof)};
  }
  return {
    provider:'stripe',environment,checkoutOrigins:[checkoutOrigin],
    checkoutOpen:env.STRIPE_CHECKOUT_ENABLED==='true',
    async createHostedSession(order,customer) {
      if (env.STRIPE_CHECKOUT_ENABLED!=='true') throw new PaymentError('checkout_not_open');
      if (order.provider!=='stripe' || order.environment!==environment || !/^[A-Z]{3}$/.test(order.currency??'') ||
        !Number.isSafeInteger(Number(order.amount_minor)) || Number(order.amount_minor)<50 ||
        Number(order.amount_minor)>99999999 || !token(order.provider_order_id,30))
        throw new PaymentError('invalid_provider_order',400);
      if (!customer || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customer.email??'') || customer.email.length>254)
        throw new PaymentError('billing_details_required',400);
      const claim=await rpc('payment_session_claim',{p_order_id:order.id});
      if (claim.state==='ready') return {url:claim.checkout_url};
      if (claim.state!=='claimed') throw new PaymentError('checkout_session_pending_review',409);
      try {
        const expiresAt=Math.min(Math.floor(Date.parse(order.expires_at)/1000),Math.floor(Date.now()/1000)+86400);
        const result=await api('/v1/checkout/sessions',{
          mode:'payment',client_reference_id:order.provider_order_id,customer_email:customer.email,
          success_url:callback.href,cancel_url:callback.href,
          expires_at:Math.max(expiresAt,Math.floor(Date.now()/1000)+1800),
          payment_method_types:{0:'card'},
          line_items:{0:{quantity:1,price_data:{currency:order.currency.toLowerCase(),unit_amount:Number(order.amount_minor),
            product_data:{name:`SafeNest protection — ${planNames[order.plan_code]??order.plan_code} plan`}}}},
          metadata:{safenest_order:order.id},
        },{method:'POST',idempotencyKey:'safenest-order-'+order.id});
        if (result.object!=='checkout.session' || !token(result.id)) throw new PaymentError('checkout_session_unavailable');
        let url;try {url=new URL(result.url);}catch {throw new PaymentError('invalid_provider_checkout');}
        if (url.origin!==checkoutOrigin || url.username || url.password) throw new PaymentError('invalid_provider_checkout');
        await rpc('payment_session_save',{p_order_id:order.id,p_session_id:result.id,p_url:url.href});
        return {url:url.href};
      } catch {
        try {await rpc('payment_session_review',{p_order_id:order.id});}catch {}
        throw new PaymentError('checkout_session_pending_review',409);
      }
    },
    async validateNotification(raw,headers) {
      await verifyStripeSignature(headers.get('stripe-signature'),raw,webhookSecret);
      let event;
      try {event=JSON.parse(raw);}catch {throw new PaymentError('invalid_notification',400);}
      if (!event || typeof event!=='object' || event.object!=='event' || event.livemode!==(environment==='live'))
        throw new PaymentError('invalid_notification',400);
      const session=event.data?.object;
      if (!['checkout.session.completed','checkout.session.async_payment_succeeded','checkout.session.expired']
        .includes(event.type) || session?.object!=='checkout.session')
        throw new PaymentError('event_ignored',200); // Acknowledged so Stripe stops retrying unrelated events.
      if (!token(session.client_reference_id,30) || !token(session.id)) throw new PaymentError('invalid_notification',400);
      const order=await rpc('payment_order_lookup',{p_reference:session.client_reference_id,p_provider:'stripe',p_environment:environment});
      if (!order) throw new PaymentError('order_not_found',404);
      if (order.session_id && order.session_id!==session.id) throw new PaymentError('payment_order_mismatch',400);
      return normalize(order,session.id);
    },
    async reconcile(order) {
      if (order.provider!=='stripe' || order.environment!==environment) throw new PaymentError('reconciliation_order_mismatch',400);
      if (!token(order.session_id)) throw new PaymentError('payment_requires_review',409);
      return normalize(order,order.session_id);
    },
  };
}
