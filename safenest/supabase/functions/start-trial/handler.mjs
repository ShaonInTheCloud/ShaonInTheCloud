import {readLimitedBody} from '../_shared/payments.mjs';
const allowed=new Set(['https://mysafenestbd.com','https://safenest-bangladesh.kabirmdhumaun23.chatgpt.site']);
export async function handleTrial(req,env,{fetcher=fetch,rpc}={}) {
  const origin=req.headers.get('origin');
  const headers={'Content-Type':'application/json','Cache-Control':'no-store',Vary:'Origin',
    ...(allowed.has(origin)?{'Access-Control-Allow-Origin':origin,'Access-Control-Allow-Headers':'authorization,apikey,content-type,x-client-info','Access-Control-Allow-Methods':'POST,OPTIONS'}:{})};
  const json=(body,status=200)=>new Response(JSON.stringify(body),{status,headers});
  if(origin&&!allowed.has(origin))return json({error:'origin_not_allowed'},403);
  if(req.method==='OPTIONS')return new Response(null,{status:204,headers});
  if(req.method!=='POST')return json({error:'method_not_allowed'},405);
  const authorization=req.headers.get('authorization')||'';
  if(!/^Bearer [A-Za-z0-9_.-]+$/.test(authorization))return json({error:'authentication_required'},401);
  try {
    const auth=await fetcher(env.SUPABASE_URL+'/auth/v1/user',{headers:{apikey:env.SUPABASE_ANON_KEY,Authorization:authorization},redirect:'error',signal:AbortSignal.timeout(10000)});
    if(!auth.ok)return json({error:'authentication_required'},401);
    const user=await auth.json();
    if(!user.id||user.is_anonymous===true||!user.email_confirmed_at)return json({error:'confirmed_account_required'},403);
    const text=await readLimitedBody(req,1024);
    let body;try{body=JSON.parse(text);}catch{return json({error:'invalid_request'},400);}
    if(!body||Array.isArray(body)||Object.keys(body).length!==1||!['monthly','quarterly','annual'].includes(body.plan_code))return json({error:'invalid_plan'},400);
    const result=await rpc('start_protection_trial',{p_user_id:user.id,p_plan_code:body.plan_code});
    return json(result);
  }catch{return json({error:'trial_unavailable'},503);}
}
