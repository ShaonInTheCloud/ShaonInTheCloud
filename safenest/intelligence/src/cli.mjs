import {createClient} from '@supabase/supabase-js';
import {readFile,writeFile} from 'node:fs/promises';
import {normalize,aliasKey,makeSnapshot,signSnapshot} from './core.mjs';
import {probe,safeGet} from './probe.mjs';
import {regulatorCandidates} from './feeds.mjs';
const db=createClient(process.env.SUPABASE_URL||'',process.env.SUPABASE_SERVICE_ROLE_KEY||'',{auth:{persistSession:false,autoRefreshToken:false}});
const checked=async q=>{const {data,error}=await q;if(error)throw Error(error.message);return data;};
async function all(table,select='*'){let rows=[];for(let from=0;;from+=500){const page=await checked(db.from(table).select(select).order('id').range(from,from+499));rows.push(...page);if(page.length<500)break;if(rows.length>100000)throw Error('Dataset exceeds publisher capacity');}return rows;}
const command=process.argv[2];
if(command==='import'){
 const seed=JSON.parse(await readFile(process.argv[3],'utf8'));
 for(const b of seed.brands){const [brand]=await checked(db.from('gambling_brands').upsert({name:b.name,slug:b.slug},{onConflict:'slug'}).select('id'));for(const alias of [b.name,...(b.aliases||[])])await checked(db.from('brand_aliases').upsert({brand_id:brand.id,alias,normalized_alias:aliasKey(alias)},{onConflict:'brand_id,normalized_alias'}));}
 const brands=new Map((await all('gambling_brands')).map(b=>[b.slug,b.id]));
 for(const d of seed.domains){const n=normalize(d.domain);const existing=await checked(db.from('gambling_domains').select('id').eq('domain',n.domain).maybeSingle());let id=existing?.id;
  if(!id){const [created]=await checked(db.from('gambling_domains').insert({domain:n.domain,registrable_domain:n.registrable_domain,brand_id:brands.get(d.brand)||null,confidence:'REVIEW_REQUIRED',active:false,notes:d.notes||''}).select('id'));id=created.id;}
  // Never silently overwrite an administrator's disposition on repeated imports.
  for(const source of d.sources||[])await checked(db.from('domain_sources').upsert({...source,domain_id:id},{onConflict:'domain_id,source_url'}));
  if(!existing&&d.confidence==='CONFIRMED')await checked(db.from('gambling_domains').update({confidence:d.confidence,active:true,last_verified:new Date().toISOString(),domain_type:'primary'}).eq('id',id));
 }
 console.log('Imported seed evidence; existing review decisions preserved.');
}else if(command==='discover'){
 const url='https://www.gamblingcommission.gov.uk/downloads/business-licence-register-domain-names.csv';
 const response=await safeGet(url,{maxBytes:4*1024*1024,timeout:15000});if(response.status!==200)throw Error('Feed unavailable');
 const candidates=regulatorCandidates(response.text);if(!candidates.length||candidates.length>20000)throw Error('Unexpected feed size');
 for(const n of candidates){await checked(db.from('gambling_domains').upsert({domain:n.domain,registrable_domain:n.registrable_domain,notes:'Regulator feed discovery; requires review of site purpose.'},{onConflict:'domain',ignoreDuplicates:true}));const row=await checked(db.from('gambling_domains').select('id').eq('domain',n.domain).single());await checked(db.from('domain_sources').upsert({domain_id:row.id,source_name:'UKGC discovery feed',source_url:url,source_type:'discovery',verified:false,evidence_summary:'Active register domain; account '+n.account+'. A reviewer must verify site purpose before promotion.'},{onConflict:'domain_id,source_url',ignoreDuplicates:true}));}
 console.log('Discovered',candidates.length,'feed candidates; no automatic activation.');
}else if(command==='probe'){
 const brands=(await all('gambling_brands','*,brand_aliases(alias)')).map(b=>({...b,aliases:b.brand_aliases.map(a=>a.alias)}));const rows=await checked(db.from('gambling_domains').select('*').order('last_seen').limit(100));const known=new Set((await all('gambling_domains')).filter(r=>r.active).map(r=>r.domain));
 for(const row of rows){let observation;try{observation=await probe(row.domain,brands,known);}catch(e){observation={dns_status:'probe_failed',classification:{error:e.message,confidence:'REVIEW_REQUIRED'}};}
  const {chain,apk_hosts,...stored}=observation;stored.classification={...stored.classification,redirect_chain:chain||[],apk_hosts:apk_hosts||[]};await checked(db.from('domain_observations').insert({...stored,domain_id:row.id,hostname:row.domain}));await checked(db.from('gambling_domains').update({last_seen:new Date().toISOString()}).eq('id',row.id));
  // Redirect and APK hosts are discovery signals, never automatically verified mirrors.
  for(const host of [stored.redirect_target,...(apk_hosts||[])].filter(Boolean)){const n=normalize(host);await checked(db.from('gambling_domains').upsert({domain:n.domain,registrable_domain:n.registrable_domain,notes:'Discovered by public probe of '+row.domain},{onConflict:'domain',ignoreDuplicates:true}));}
 }
 console.log('Probed',rows.length,'domains; no automated promotion to active.');
}else if(command==='publish'){
 const latest=await checked(db.from('blocklist_versions').select('version,public_key,domains').order('version',{ascending:false}).limit(1));
 const rows=await all('gambling_domains','*,sources:domain_sources(*),gambling_brands(name)');
 const snapshot=makeSnapshot(rows.map(r=>({...r,brand:r.gambling_brands?.name||null})),Number(latest[0]?.version||0)+1);
 const pem=await readFile(process.env.SAFENEST_SIGNING_KEY_FILE,'utf8');const signed=signSnapshot(snapshot,pem);
 if(latest[0]&&latest[0].public_key.trim()!==signed.public_key.trim())throw Error('Key rotation requires explicit client pin migration');
 if(latest[0]&&snapshot.domains.length<latest[0].domains.length*0.8)throw Error('More than 20% removal: review before release');
 const {payload,...record}=signed;await checked(db.from('blocklist_versions').insert({...record,domain_count:record.domains.length}));
 if(process.env.SAFENEST_OUTPUT_FILE)await writeFile(process.env.SAFENEST_OUTPUT_FILE,signed.envelope,{flag:'wx'});
 console.log('Published signed revision',snapshot.version,'with',snapshot.domains.length,'domains');
}else{throw Error('Usage: node src/cli.mjs import <seed.json> | probe | publish');}
