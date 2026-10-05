import {createClient} from 'npm:@supabase/supabase-js@2.117.1';
import {parse} from 'npm:tldts@7.0.25';
const headers={'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'apikey, authorization, content-type','Access-Control-Allow-Methods':'GET, OPTIONS','Content-Type':'application/json','Cache-Control':'no-store'};
const json=(data:unknown,status=200)=>new Response(JSON.stringify(data),{status,headers});
const db=createClient(Deno.env.get('SUPABASE_URL')!,Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!,{auth:{persistSession:false,autoRefreshToken:false}});
let cached:any=null;let cachedAt=0;let lookupMap=new Map<string,any>();
async function latest(){if(cached&&Date.now()-cachedAt<60000)return cached;const {data,error}=await db.from('blocklist_versions').select('version,generated_at,expires_at,checksum,domains,envelope').order('version',{ascending:false}).limit(1).maybeSingle();if(error)throw Error('Unavailable');if(data){cached=data;lookupMap=new Map(data.domains.map((r:any)=>[r.domain,r]));cachedAt=Date.now();}return data;}
Deno.serve(async req=>{
 if(req.method==='OPTIONS')return new Response(null,{status:204,headers});
 if(req.method!=='GET')return json({error:'Method not allowed'},405);
 const url=new URL(req.url);
 // Public catalogue access key only; never accepts a service-role key from callers.
 // This is API key authentication, not an admin authorization mechanism.
 const provided=req.headers.get('apikey')||url.searchParams.get('key');
 const expected=Deno.env.get('SAFENEST_CATALOG_ACCESS_KEY')||Deno.env.get('SUPABASE_ANON_KEY');
 if(!expected||!provided||provided!==expected)return json({error:'Catalogue API key required'},401);
 try{
  const current=await latest();if(!current)return json({error:'No signed release has been published'},503);
  const action=url.pathname.split('/').filter(Boolean).pop();
  if(action==='catalog')return new Response(current.envelope,{headers:{...headers,'Content-Type':'application/octet-stream','Cache-Control':'public, max-age=60','ETag':'"'+current.checksum+'"'}});
  if(action==='check'){
   const raw=url.searchParams.get('domain')||'';if(raw.length>253||/[\s/:@?#\\]/.test(raw))return json({error:'Hostname required'},400);
   let host;try{host=new URL('https://'+raw).hostname.toLowerCase().replace(/\.$/,'').replace(/^www\./,'');}catch{return json({error:'Invalid hostname'},400);}
   const parsed=parse(host,{allowPrivateDomains:true});if(!parsed.domain||parsed.isIp||(!parsed.isIcann&&!parsed.isPrivate))return json({error:'Invalid hostname'},400);
   while(true){const found=lookupMap.get(host);if(found)return json({blocked:true,...found,reason:'Known gambling domain',blocklist_version:current.version});if(host===parsed.domain)break;host=host.slice(host.indexOf('.')+1);}
   return json({blocked:false,domain:raw,blocklist_version:current.version});
  }
  if(action==='delta'){
   const since=Number(url.searchParams.get('since'));if(!Number.isSafeInteger(since)||since<1)return json({error:'Valid since revision required'},400);
   if(since===current.version)return json({since,version:since,added:[],removed:[],target_checksum:current.checksum});
   const {data:before,error}=await db.from('blocklist_versions').select('domains,version').eq('version',since).maybeSingle();if(error)throw Error('Unavailable');if(!before||since>current.version)return json({error:'Unknown revision; download full catalogue'},409);
   const old=new Set(before.domains.map((r:any)=>r.domain)),now=new Set(current.domains.map((r:any)=>r.domain));
   // Informational delta. Android installs the signed full envelope; never applies unsigned changes.
   return json({since,version:current.version,added:current.domains.filter((r:any)=>!old.has(r.domain)),removed:[...old].filter(d=>!now.has(d)),target_checksum:current.checksum,requires_signed_full_catalog:true});
  }
  if(action==='blocklist'||action==='intelligence')return json({version:current.version,generated_at:current.generated_at,expires_at:current.expires_at,checksum:current.checksum,domains:current.domains});
  return json({error:'Not found'},404);
 }catch{return json({error:'Catalogue temporarily unavailable; retain the last verified local list'},503);}
});
