import {parse} from 'tldts';
import {domainToASCII} from 'node:url';
import {createHash,createPrivateKey,createPublicKey,sign,verify} from 'node:crypto';
export const STATES=['CONFIRMED','HIGH','SUSPECTED','MIRROR','DEAD','FALSE_POSITIVE','REVIEW_REQUIRED'];
export function normalize(raw) {
  if(typeof raw!=='string'||raw.length>2048||/[\s\\\x00-\x1f]/u.test(raw.trim())) throw Error('Invalid domain');
  let u; try {u=new URL(raw.includes('://')?raw.trim():'https://'+raw.trim());} catch {throw Error('Invalid URL');}
  if(!['https:','http:'].includes(u.protocol)||u.username||u.password||u.port) throw Error('Only HTTP(S) domain inputs without credentials or ports');
  const hostname=domainToASCII(u.hostname.replace(/\.$/,'')).toLowerCase();
  const p=parse(hostname,{allowPrivateDomains:true});
  if(p.isIp||!p.domain||(!p.isIcann&&!p.isPrivate)||hostname.length>253||hostname.split('.').some(x=>! /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/.test(x))) throw Error('Not a registrable public hostname');
  return {hostname,domain:hostname.startsWith('www.')?hostname.slice(4):hostname,registrable_domain:p.domain,private_suffix:p.isPrivate};
}
export const aliasKey=s=>s.normalize('NFKC').toLowerCase().replace(/[^\p{L}\p{N}]/gu,'');
export function distance(a,b){if(Math.abs(a.length-b.length)>3)return 4;let prev=Array.from({length:b.length+1},(_,i)=>i);for(let i=1;i<=a.length;i++){let cur=[i];for(let j=1;j<=b.length;j++)cur[j]=Math.min(prev[j]+1,cur[j-1]+1,prev[j-1]+Number(a[i-1]!==b[j-1]));prev=cur;}return prev[b.length];}
export function brandMatches(host,brands){const label=aliasKey(parse(normalize(host).domain,{allowPrivateDomains:true}).domainWithoutSuffix||'');return brands.filter(b=>[b.name,...(b.aliases||[])].some(a=>{let k=aliasKey(a);return k.length>=4&&(label===k||distance(label,k)<=1||new RegExp('^'+k+'(?:bd|bangla|live|vip|official|app|play|[0-9])+$').test(label));})).map(b=>b.slug);}
export function classify({host,title='',text='',brands=[],redirectKnown=false}){
 const content=(title+' '+text).slice(0,200000).toLowerCase();
 const groups=[/sportsbook|sports betting|ক্রিকেট বাজি/,/roulette|blackjack|baccarat|স্লট/,/live casino|online casino|অনলাইন ক্যাসিনো/,/deposit|withdraw|জমা|উত্তোলন/,/odds|jackpot|বোনাস/];
 const signals=groups.map((r,i)=>r.test(content)?i:null).filter(x=>x!==null);
 const matches=brandMatches(host,brands); const score=Math.min(99,signals.length*10+(matches.length?20:0)+(redirectKnown?30:0));
 return {confidence:score>=40?'SUSPECTED':'REVIEW_REQUIRED',score,brand_matches:matches,signal_groups:signals,redirect_to_known:redirectKnown,active:false};
}
export function eligible(rows,now=new Date()) {return rows.filter(r=>r.active&&['CONFIRMED','HIGH','MIRROR'].includes(r.confidence)&&r.last_verified&&Date.parse(r.last_verified)>=+now-90*86400000&&r.sources?.some(s=>s.verified&&['regulator','government','reporting','operator'].includes(s.source_type)&&s.source_url?.startsWith('https://')&&s.evidence_summary?.length>=20)).map(r=>({...r,...normalize(r.domain)}));}
export function makeSnapshot(rows,revision,now=new Date()){
 if(!Number.isSafeInteger(revision)||revision<=0)throw Error('Invalid revision');
 const domains=[...new Map(eligible(rows,now).map(r=>[r.domain,{domain:r.domain,brand:r.brand||null,confidence:r.confidence,category:'gambling'}])).values()].sort((a,b)=>a.domain.localeCompare(b.domain,'en'));
 if(!domains.length||domains.length>100000)throw Error('Refusing empty or oversized release');
 const issued=now.toISOString().replace(/\.\d{3}Z$/,'Z'),expires=new Date(+now+30*86400000).toISOString().replace(/\.\d{3}Z$/,'Z');
 const payload=`SAFENEST-CATALOG/1\nrevision:${revision}\nissued-at:${issued}\nexpires-at:${expires}\n`+domains.map(d=>'gambling:'+d.domain).sort().join('\n')+'\n';
 return {version:revision,generated_at:issued,expires_at:expires,domains,payload,checksum:createHash('sha256').update(payload).digest('hex')};
}
export function signSnapshot(snapshot,pem){const key=createPrivateKey(pem);if(key.asymmetricKeyDetails?.namedCurve!=='prime256v1')throw Error('P-256 required'); const signature=sign('sha256',Buffer.from(snapshot.payload),key).toString('base64');return {...snapshot,envelope:`SAFENEST-SIGNED-CATALOG/1\npayload:${Buffer.from(snapshot.payload).toString('base64')}\nsignature:${signature}\n`,public_key:createPublicKey(key).export({type:'spki',format:'pem'})};}
export function verifyEnvelope(envelope,key){const lines=envelope.trimEnd().split('\n');if(lines.length!==3||lines[0]!=='SAFENEST-SIGNED-CATALOG/1')return false;return verify('sha256',Buffer.from(lines[1].slice(8),'base64'),key,Buffer.from(lines[2].slice(10),'base64'));}
export function delta(previous,current){if(previous.version>=current.version)throw Error('Invalid delta order');const before=new Set(previous.domains.map(d=>d.domain)),after=new Set(current.domains.map(d=>d.domain));return {since:previous.version,version:current.version,added:current.domains.filter(d=>!before.has(d.domain)),removed:[...before].filter(d=>!after.has(d)),target_checksum:current.checksum};}
export function lookup(raw,rows,version){const n=normalize(raw),map=rows instanceof Map?rows:new Map(rows.map(d=>[d.domain,d]));let h=n.domain;while(true){if(map.has(h))return {blocked:true,...map.get(h),reason:'Known gambling domain',blocklist_version:version};if(h===n.registrable_domain)break;h=h.slice(h.indexOf('.')+1);}return {blocked:false,domain:n.domain,blocklist_version:version};}
