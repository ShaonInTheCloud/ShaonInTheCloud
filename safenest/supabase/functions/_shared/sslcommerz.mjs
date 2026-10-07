import {PaymentError, readLimitedBody} from './payments.mjs';

export function moneyMinor(value) {
  if (typeof value!=='string' || !/^(0|[1-9][0-9]{0,7})(\.[0-9]{1,2})?$/.test(value))
    throw new PaymentError('invalid_provider_amount',400);
  const [whole,fraction='']=value.split('.');
  const result=Number(whole)*100+Number(fraction.padEnd(2,'0'));
  if (!Number.isSafeInteger(result) || result<=0) throw new PaymentError('invalid_provider_amount',400);
  return result;
}
export function providerTime(value) {
  // Operator must confirm the merchant API's timestamp zone before enabling.
  if (typeof value!=='string' || !/^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d$/.test(value))
    throw new PaymentError('invalid_provider_time',400);
  const time=new Date(value.replace(' ','T')+'+06:00');
  if (!Number.isFinite(time.valueOf()) || new Date(time.valueOf()+6*3600000).toISOString().slice(0,19)!==value.replace(' ','T'))
    throw new PaymentError('invalid_provider_time',400);
  return time.toISOString();
}
const token=(v,max=200)=>typeof v==='string' && /^[A-Za-z0-9_-]+$/.test(v) && v.length<=max;
async function digest(value) {
  const data=await crypto.subtle.digest('SHA-256',new TextEncoder().encode(JSON.stringify(value)));
  return Array.from(new Uint8Array(data),b=>b.toString(16).padStart(2,'0')).join('');
}
/** Internal independent-verification boundary; never pass browser/callback fields here. */
export async function normalizeVerifiedRecovery(purchase, recovery) {
  const proof={...purchase,status:'recovery',kind:recovery.kind,recoveryId:recovery.id,
    recoveryState:recovery.state,recoveryAmountMinor:recovery.amountMinor};
  delete proof.eventId; delete proof.evidenceSha256;
  const hash=await digest(proof);
  return {...proof,eventId:'recovery:'+hash,evidenceSha256:hash};
}
export function sslcommerzAdapter(env,rpc,fetcher=fetch) {
  if (env.SSLCOMMERZ_ENABLED!=='true') throw new PaymentError('payment_provider_not_configured');
  const environment=env.SSLCOMMERZ_ENVIRONMENT;
  if (!['sandbox','live'].includes(environment) || (environment==='live' && env.SSLCOMMERZ_LIVE_APPROVED!=='true'))
    throw new PaymentError('payment_provider_not_configured');
  const prefix=environment==='live'?'SSLCOMMERZ_LIVE_':'SSLCOMMERZ_SANDBOX_';
  const store=env[prefix+'STORE_ID'],password=env[prefix+'STORE_PASSWORD'];
  if (!token(store,30) || typeof password!=='string' || !password || password.length>200 ||
      env.SSLCOMMERZ_TIMESTAMP_ZONE!=='Asia/Dhaka' || !env.SUPABASE_URL)
    throw new PaymentError('payment_provider_not_configured');
  const base=environment==='live'?'https://securepay.sslcommerz.com':'https://sandbox.sslcommerz.com';
  const callback=new URL('/functions/v1/payment-return',env.SUPABASE_URL).href;
  const ipn=new URL('/functions/v1/payment-notification',env.SUPABASE_URL).href;
  if (!callback.startsWith('https://') || callback.length>255 || ipn.length>255)
    throw new PaymentError('payment_provider_not_configured');
  async function api(path,fields,method='GET') {
    const params=new URLSearchParams({...fields,store_id:store,store_passwd:password,format:'json'});
    const url=base+path+(method==='GET'?'?'+params:'');
    const response=await fetcher(url,{method,redirect:'error',signal:AbortSignal.timeout(15000),
      ...(method==='POST'?{headers:{'Content-Type':'application/x-www-form-urlencoded'},body:params.toString()}:{})});
    if (!response.ok) throw new PaymentError('payment_provider_unavailable');
    const text=await readLimitedBody(response,65536);
    try {const obj=JSON.parse(text);if (!obj || Array.isArray(obj) || typeof obj!=='object') throw 0; return obj;}
    catch {throw new PaymentError('invalid_provider_response');}
  }
  async function normalize(order,valId) {
    if (!token(valId)) throw new PaymentError('invalid_notification',400);
    const result=await api('/validator/api/validationserverAPI.php',{val_id:valId});
    if (!['VALID','VALIDATED'].includes(result.status)) throw new PaymentError('payment_not_validated',409);
    if (result.APIConnect!=='DONE' || result.val_id!==valId || result.tran_id!==order.provider_order_id ||
      (result.store_id!==undefined && result.store_id!==store) || result.currency!=='BDT' ||
      result.currency_type!=='BDT' || moneyMinor(result.amount)!==Number(order.amount_minor) ||
      moneyMinor(result.currency_amount)!==Number(order.amount_minor) || order.currency!=='BDT')
      throw new PaymentError('payment_order_mismatch',400);
    if (String(result.risk_level)!=='0') throw new PaymentError('payment_requires_review',409);
    if (!token(result.bank_tran_id)) throw new PaymentError('invalid_provider_reference',400);
    const proof={orderId:order.id,provider:'sslcommerz',environment,status:'paid',
      amountMinor:Number(order.amount_minor),currency:'BDT',transactionId:result.bank_tran_id,
      paidAt:providerTime(result.tran_date),validationReference:valId};
    // VALID and VALIDATED are equivalent. No card fields or callback data enter the hash.
    return {...proof,eventId:'paid:'+result.bank_tran_id,evidenceSha256:await digest(proof)};
  }
  async function refund(order, valId, reference) {
    if (!token(reference,50)) throw new PaymentError('invalid_recovery_reference',400);
    const purchase=await normalize(order,valId);
    const result=await api('/validator/api/merchantTransIDvalidationAPI.php',{refund_ref_id:reference});
    const state={refunded:'completed',processing:'pending',cancelled:'cancelled'}[result.status];
    if (result.APIConnect!=='DONE' || !state || result.refund_ref_id!==reference ||
        result.tran_id!==order.provider_order_id || result.bank_tran_id!==purchase.transactionId ||
        (result.store_id!==undefined && result.store_id!==store))
      throw new PaymentError('invalid_recovery_evidence',400);
    // The published query example omits refund_amount. Never substitute callback
    // amount or original price. Missing amount remains an explicit review item.
    const amount=result.refund_amount===undefined ? null : moneyMinor(result.refund_amount);
    if (amount!==null && amount>purchase.amountMinor) throw new PaymentError('invalid_recovery_evidence',400);
    return normalizeVerifiedRecovery(purchase,{kind:'refund',id:reference,state,amountMinor:amount});
  }
  return {
    provider:'sslcommerz',environment,checkoutOrigins:[base],
    async createHostedSession(order,customer) {
      if (env.SSLCOMMERZ_CHECKOUT_ENABLED!=='true') throw new PaymentError('checkout_not_open');
      if (order.provider!=='sslcommerz' || order.environment!==environment || order.currency!=='BDT' ||
        !Number.isSafeInteger(Number(order.amount_minor)) || Number(order.amount_minor)<1000 ||
        Number(order.amount_minor)>50000000 || !token(order.provider_order_id,30))
        throw new PaymentError('invalid_provider_order',400);
      const required={name:100,email:254,phone:30,address:200,city:100};
      if (!customer || Object.entries(required).some(([k,max])=>typeof customer[k]!=='string' ||
        !customer[k].trim() || customer[k].length>max || /[\x00-\x1f]/.test(customer[k])) ||
        !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customer.email)) throw new PaymentError('billing_details_required',400);
      const claim=await rpc('payment_session_claim',{p_order_id:order.id});
      if (claim.state==='ready') return {url:claim.checkout_url};
      if (claim.state!=='claimed') throw new PaymentError('checkout_session_pending_review',409);
      try {
        const result=await api('/gwprocess/v4/api.php',{total_amount:(Number(order.amount_minor)/100).toFixed(2),
          currency:'BDT',tran_id:order.provider_order_id,success_url:callback,fail_url:callback,cancel_url:callback,
          ipn_url:ipn,shipping_method:'NO',product_name:'SafeNest protection',product_category:'Software',
          product_profile:'non-physical-goods',cus_name:customer.name,cus_email:customer.email,cus_phone:customer.phone,
          cus_add1:customer.address,cus_city:customer.city,cus_country:'Bangladesh'},'POST');
        if (result.status!=='SUCCESS' || !token(result.sessionkey)) throw new PaymentError('checkout_session_unavailable');
        let url;try {url=new URL(result.GatewayPageURL);}catch {throw new PaymentError('invalid_provider_checkout');}
        if (url.origin!==base || url.username || url.password || url.hash) throw new PaymentError('invalid_provider_checkout');
        await rpc('payment_session_save',{p_order_id:order.id,p_session_id:result.sessionkey,p_url:url.href});
        return {url:url.href};
      } catch {
        // A timeout can follow a successful provider call. Never make a second
        // payable session blindly; reconcile or review the original transaction.
        try {await rpc('payment_session_review',{p_order_id:order.id});}catch {}
        throw new PaymentError('checkout_session_pending_review',409);
      }
    },
    async validateNotification(raw,headers) {
      if (!(headers.get('content-type')??'').toLowerCase().startsWith('application/x-www-form-urlencoded'))
        throw new PaymentError('invalid_notification',400);
      const fields=new URLSearchParams(raw);
      if (new Set(fields.keys()).size!==Array.from(fields.keys()).length ||
        !token(fields.get('tran_id'),30) || !token(fields.get('val_id')))
        throw new PaymentError('invalid_notification',400);
      const order=await rpc('payment_order_lookup',{p_reference:fields.get('tran_id'),p_environment:environment});
      if (!order) throw new PaymentError('order_not_found',404);
      if (fields.has('refund_ref_id')) return refund(order,fields.get('val_id'),fields.get('refund_ref_id'));
      // Public API documentation supplies no authenticated chargeback query/contract.
      // Reject that claim until a merchant-tested verifier exists; paid validation
      // alone cannot validate a dispute or grant in response to such a callback.
      if (/chargeback|dispute|refund/i.test(fields.get('status')??''))
        throw new PaymentError('recovery_verification_unavailable',409);
      return normalize(order,fields.get('val_id'));
    },
    async reconcile(order) {
      if (order.provider!=='sslcommerz' || order.environment!==environment) throw new PaymentError('reconciliation_order_mismatch',400);
      const result=await api('/validator/api/merchantTransIDvalidationAPI.php',{tran_id:order.provider_order_id});
      const matches=Array.isArray(result.element)?result.element.filter(x=>
        x.tran_id===order.provider_order_id && ['VALID','VALIDATED'].includes(x.status)):[];
      if (result.APIConnect!=='DONE' || matches.length!==1) throw new PaymentError('payment_requires_review',409);
      const evidence=[await normalize(order,matches[0].val_id)];
      const recoveries=order.recoveries??[];
      if (!Array.isArray(recoveries) || recoveries.length>12) throw new PaymentError('payment_requires_review',409);
      for (const recovery of recoveries) {
        if (recovery.kind!=='refund') throw new PaymentError('recovery_verification_unavailable',409);
        evidence.push(await refund(order,matches[0].val_id,recovery.recovery_id));
      }
      return evidence.length===1?evidence[0]:evidence;
    },
  };
}
