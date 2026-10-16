import {PaymentError,configuredAdapters,readLimitedBody,reconcileOrder,serviceRpc,uuid} from './payments.mjs';
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
export async function handleCatalogue(req,env,{fetcher=fetch,rpc=serviceRpc(env,fetcher),adapters}={}) {
  const early=preflight(req,'GET');if(early)return early;
  try {
    // Only providers whose checkout the operator opened are offered; the product list is
    // filtered to those providers so the browser never sees an unpayable price.
    const open=(adapters??configuredAdapters(env,rpc,fetcher)).filter(a=>a.checkoutOpen===true);
    if(open.length===0) throw new PaymentError('checkout_not_open');
    const environments=new Set(open.map(a=>a.environment));
    if(environments.size!==1) throw new PaymentError('payment_provider_not_configured');
    const [environment]=environments;
    const names=new Set(open.map(a=>a.provider));
    const products=(await rpc('payment_product_catalogue',{p_environment:environment}))
      .map(p=>({...p,providers:(p.providers??[]).filter(name=>names.has(name))})).filter(p=>p.providers.length>0);
    const providers=open.map(a=>({provider:a.provider,checkout_origins:a.checkoutOrigins}));
    return response(req,{available:products.length>0,environment,products,providers});
  } catch {return response(req,{available:false,products:[],providers:[]});}
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
  // Stripe and NOWPayments redirect with GET only; their verification arrives by webhook/IPN.
  return new Response(null,{status:303,headers:{Location:origin+'/account.html?payment=checking',
    'Cache-Control':'no-store','Referrer-Policy':'no-referrer'}});
}
export async function handleReconcile(req,env,{fetcher=fetch,rpc=serviceRpc(env,fetcher),adapters}={}) {
  if(req.method!=='POST')return response(req,{error:'method_not_allowed'},405);
  const expected=env.PAYMENT_RECONCILE_KEY||env.SSLCOMMERZ_RECONCILE_KEY;
  const actual=req.headers.get('x-reconcile-key')??'';
  if(typeof expected!=='string'||expected.length<32||expected.length!==actual.length)return response(req,{error:'unauthorized'},401);
  let difference=0;for(let i=0;i<expected.length;i++)difference|=expected.charCodeAt(i)^actual.charCodeAt(i);
  if(difference)return response(req,{error:'unauthorized'},401);
  try {
    if((await readLimitedBody(req))!=='')throw new PaymentError('invalid_request',400);
    const configured=adapters??configuredAdapters(env,rpc,fetcher);
    if(configured.length===0)throw new PaymentError('payment_provider_not_configured');
    let processed=0,review=0;
    for(const adapter of configured) {
      const orders=await rpc('payment_reconciliation_batch',{p_provider:adapter.provider,p_environment:adapter.environment});
      await Promise.all(orders.map(async order=>{try{await reconcileOrder(adapter,order,rpc);processed++;}catch{review++;}}));
    }
    return response(req,{processed,review});
  } catch(e){return response(req,{error:e instanceof PaymentError?e.code:'payment_backend_unavailable'},e instanceof PaymentError?e.status:503);}
}
