import dns from 'node:dns/promises';
import http from 'node:http';
import https from 'node:https';
import ipaddr from 'ipaddr.js';
import {normalize,classify} from './core.mjs';
export function publicAddress(address){try{return ipaddr.parse(address).range()==='unicast';}catch{return false;}}
export async function safeGet(raw,{maxBytes=1048576,timeout=8000}={}){
 const url=new URL(raw); normalize(url.href);
 const records=await Promise.race([dns.lookup(url.hostname,{all:true}),new Promise((_,r)=>{const t=setTimeout(()=>r(Error('DNS timeout')),timeout);t.unref();})]);
 if(!records.length||records.some(r=>!publicAddress(r.address)))throw Error('Nonpublic destination rejected');
 const pinned=records[0];
 return new Promise((resolve,reject)=>{const req=(url.protocol==='https:'?https:http).request(url,{method:'GET',agent:false,headers:{'User-Agent':'SafeNest-Research/0.1','Accept':'text/html,text/plain,application/json','Accept-Encoding':'identity'},lookup:(_h,opts,cb)=>opts.all?cb(null,[pinned]):cb(null,pinned.address,pinned.family)},res=>{
  let size=0,parts=[];const type=String(res.headers['content-type']||'');
  if(res.headers['content-encoding']&&res.headers['content-encoding']!=='identity'){req.destroy(Error('Compressed response rejected'));return;}
  if(!/text\/|application\/(json|xhtml\+xml)/i.test(type)&&!res.headers.location){req.destroy(Error('Nontext response rejected'));return;}
  res.on('data',c=>{size+=c.length;if(size>maxBytes)req.destroy(Error('Response too large'));else parts.push(c);});
  res.on('error',reject);res.on('end',()=>resolve({status:res.statusCode,location:res.headers.location,text:Buffer.concat(parts).toString('utf8'),ips:records.map(r=>r.address)}));
 });const timer=setTimeout(()=>req.destroy(Error('Request timeout')),timeout);req.on('close',()=>clearTimeout(timer));req.on('error',reject);req.end();});
}
export async function probe(host,brands=[],known=new Set()){
 let url='https://'+normalize(host).hostname,chain=[],seen=new Set();
 for(let step=0;step<5;step++){
  const canonical=normalize(url).hostname;if(seen.has(canonical))throw Error('Redirect loop');seen.add(canonical);
  const result=await safeGet(url);chain.push({hostname:canonical,http_status:result.status,resolved_ips:result.ips});
  if(result.status>=300&&result.status<400&&result.location){const target=new URL(result.location,url);normalize(target.href);url=target.href;continue;}
  const title=(result.text.match(/<title[^>]*>([\s\S]*?)<\/title>/i)||[])[1]||'';
  const text=result.text.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi,' ').replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi,' ').replace(/<[^>]*>/g,' ');
  const apk_links=[...result.text.matchAll(/href=["']([^"']+\.apk(?:\?[^"']*)?)["']/gi)].slice(0,20).flatMap(m=>{try{return [normalize(new URL(m[1],url).href).hostname];}catch{return [];}});
  return {dns_status:'resolved',resolved_ips:result.ips,http_status:result.status,redirect_target:normalize(url).hostname,classification:classify({host,title,text,brands,redirectKnown:known.has(normalize(url).domain)}),chain,apk_hosts:[...new Set(apk_links)]};
 }throw Error('Too many redirects');
}
