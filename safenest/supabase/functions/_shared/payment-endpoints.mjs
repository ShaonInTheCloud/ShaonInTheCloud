import {PaymentError,configuredAdapter,readLimitedBody,reconcileOrder,serviceRpc,uuid} from './payments.mjs';
import {handleNotification} from '../payment-notification/handler.mjs';
const origin='https://mysafenestbd.com';
function response(req,body,status=200) {
  return new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json',
    'Cache-Control':'no-store',Vary:'Origin',...(req.headers.get('origin')===origin?{
      'Access-Control-Allow-Origin':origin,'Access-Control-Allow-Headers':'authorization,apikey,content-type',
      'Access-Control-Allow-Methods':'GET,POST,OPTIONS'}:{})}});
}
function preflight(req,method) {
  if (req.headers.get('origin') && req.headers.get('origin')!==origin) return response(req,{error:'origin_not_allowed'},403);
  if(req.method==='OPTIONS') return new Response(null,{status:204,headers:{
    'Access-Control-Allow-Origin':origin,'Access-Control-Allow-Headers':'authorization,apikey,content-type',
    'Access-Control-Allow-Methods':'GET,POST,OPTIONS',Vary:'Origin'}});
  if(req.method!==method) return response(req,{error:'method_not_allowed'},405);
}
export async function handleCatalogue(req,env,{fetcher=fetch,rpc=serviceRpc(env,fetcher)}={}) {
  const early=preflight(req,'GET');if(early)return early;
  try {
    const adapter=configuredAdapter(env,rpc,fetcher);
    if(env.SSLCOMMERZ_CHECKOUT_ENABLED!=='true') throw new PaymentError('checkout_not_open');
    const products=await rpc('payment_product_catalogue',{p_environment:adapter.environment});
    return response(req,{available:products.length>0,environment:adapter.environment,products});
  } catch {return response(req,{available:false,products:[]});}
}
export async function handleStatus(req,env,{fetcher=fetch,rpc=serviceRpc(env,fetcher)}={}) {
  const early=preflight(req,'POST');if(early)return early;
  try {
    const authorization=req.headers.get('authorization')??'';
    if(!/^Bearer [A-Za-z0-9_.-]+$/.test(authorization)) throw new PaymentError('authentication_required',401);
    let key=env.SUPABASE_ANON_KEY;try{key=JSON.parse(env.SUPABASE_PUBLISHABLE_KEYS||'{}').default||key;}catch{}
    if(!key||!env.SUPABASE_URL)throw new PaymentError('payment_backend_unavailable');
    const auth=await fetcher(env.SUPABASE_URL+'/auth/v1/user',{headers:{apikey:key,Authorization:authorization},
      redirect:'error',signal:AbortSignal.timeout(10000)});
    if(!auth.ok)throw new PaymentError('authentication_required',401);
    const user=await auth.json();
    if(!uuid.test(user.id??'')||user.is_anonymous===true||!user.email_confirmed_at)throw new PaymentError('confirmed_account_required',403);
    let body;try{body=JSON.parse(await readLimitedBody(req));}catch{throw new PaymentError('invalid_request',400);}
    if(!body||Object.keys(body).length!==1||!uuid.test(body.order_id??''))throw new PaymentError('invalid_request',400);
    const result=await rpc('payment_order_status',{p_order_id:body.order_id,p_user_id:user.id});
    return response(req,result??{error:'order_not_found'},result?200:404);
  } catch(e){return response(req,{error:e instanceof PaymentError?e.code:'payment_backend_unavailable'},e instanceof PaymentError?e.status:503);}
}
export async function handleReturn(req,env,deps={}) {
  if(!['GET','POST'].includes(req.method))return response(req,{error:'method_not_allowed'},405);
  // Browser state never confirms payment. POST independently verifies as an IPN backup.
  if(req.method==='POST') await handleNotification(req,env,deps);
  return new Response(null,{status:303,headers:{Location:origin+'/account.html?payment=checking',
    'Cache-Control':'no-store','Referrer-Policy':'no-referrer'}});
}
export async function handleReconcile(req,env,{fetcher=fetch,rpc=serviceRpc(env,fetcher),
  adapterFor=(boundedFetch)=>configuredAdapter(env,rpc,boundedFetch)}={}) {
  if(req.method!=='POST')return response(req,{error:'method_not_allowed'},405);
  const expected=env.SSLCOMMERZ_RECONCILE_KEY;
  const actual=req.headers.get('x-reconcile-key')??'';
  if(typeof expected!=='string'||expected.length<32||expected.length!==actual.length)return response(req,{error:'unauthorized'},401);
  let difference=0;for(let i=0;i<expected.length;i++)difference|=expected.charCodeAt(i)^actual.charCodeAt(i);
  if(difference)return response(req,{error:'unauthorized'},401);
  try {
    const body=(await readLimitedBody(req,1024)).trim();
    // pg_net sends an empty object; neither form accepts order IDs or caller options.
    if(body!=='' && body!=='{}')throw new PaymentError('invalid_request',400);
    const deadline=AbortSignal.timeout(45000);
    const boundedFetch=(url,init={})=>fetcher(url,{...init,
      signal:AbortSignal.any([deadline,...(init.signal?[init.signal]:[])])});
    const adapter=adapterFor(boundedFetch);
    const orders=await rpc('payment_reconciliation_batch',{p_environment:adapter.environment});
    if(!Array.isArray(orders)||orders.length>12||orders.some(o=>!uuid.test(o?.id??'')||
      !uuid.test(o?.reconciliation_lease??'')))throw new PaymentError('invalid_reconciliation_batch');
    let processed=0,review=0,retry=0,unfinished=0;
    const queue=[...orders];
    async function worker() {
      while(queue.length) {
        const order=queue.shift();let result='retry';
        try {
          if(deadline.aborted)throw new PaymentError('reconciliation_deadline');
          const outcome=await reconcileOrder(adapter,order,(name,args)=>{
            deadline.throwIfAborted();return rpc(name,args);
          });
          result=['recovery_requires_review','recovery_review_prevents_new_grant'].includes(outcome?.action)?'review':'processed';
        } catch(e) {
          // Invalid/ambiguous verification is review; provider/DB outages are retries.
          result=e instanceof PaymentError && e.status<500?'review':'retry';
        }
        try {
          const saved=await rpc('payment_reconciliation_finish',{p_order_id:order.id,
            p_lease:order.reconciliation_lease,p_result:result});
          if(saved!==true) {unfinished++;continue;}
          if(result==='processed')processed++;else if(result==='review')review++;else retry++;
        } catch {unfinished++;} // Expiring lease recovers crashes/ambiguous DB completion.
      }
    }
    await Promise.all(Array.from({length:Math.min(3,queue.length)},worker));
    return response(req,{processed,review,retry,unfinished},retry||unfinished?503:200);
  } catch(e){return response(req,{error:e instanceof PaymentError?e.code:'payment_backend_unavailable'},e instanceof PaymentError?e.status:503);}
}
