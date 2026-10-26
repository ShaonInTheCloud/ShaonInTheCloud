import test from 'node:test';
import assert from 'node:assert/strict';
import {runReconciliation,reconcileUrl} from '../scripts/payment-reconcile.mjs';

const key='local-fixture-reconcile-secret-0001';
const env={RECONCILE_ENABLED:'true',RECONCILE_URL:reconcileUrl,RECONCILE_KEY:key};

test('scheduler is inert without explicit enablement or when both secrets are absent',async()=>{
  let requests=0;const fetcher=async()=>{requests++;};
  for(const change of [{RECONCILE_ENABLED:''},{RECONCILE_ENABLED:'false'},
    {RECONCILE_URL:'',RECONCILE_KEY:''},
    {RECONCILE_ENABLED:'false',RECONCILE_URL:'',RECONCILE_KEY:key}])
    await runReconciliation({...env,...change},fetcher,()=>{});
  assert.equal(requests,0);
});

test('enabled scheduler reports partial configuration before making any request',async()=>{
  let requests=0;const fetcher=async()=>{requests++;};
  for(const change of [{RECONCILE_URL:''},{RECONCILE_KEY:''}])
    await assert.rejects(runReconciliation({...env,...change},fetcher,()=>{}),/configuration is incomplete/);
  assert.equal(requests,0);
});

test('scheduler refuses unintended hosts, URL ambiguities and unsafe keys before sending a credential',async()=>{
  let requests=0;const fetcher=async()=>{requests++;};
  for(const url of ['https://attacker.example/.supabase.co/functions/v1/payment-reconcile',
    'https://attacker.invalid/#.supabase.co/functions/v1/payment-reconcile',
    'https://attacker.invalid/?next=.supabase.co/functions/v1/payment-reconcile',
    reconcileUrl.replace('.supabase.co','.supabase.co.attacker.invalid'),
    'https://other.supabase.co/functions/v1/payment-reconcile',
    reconcileUrl+'?redirect=https://attacker.example',reconcileUrl+'#fragment',
    reconcileUrl.replace('https://','http://'),reconcileUrl.replace('https://','https://user@')])
    await assert.rejects(runReconciliation({...env,RECONCILE_URL:url},fetcher,()=>{}),/exactly match/);
  for(const key of ['short','a'.repeat(201),'a'.repeat(32)+'\n','a'.repeat(32)+'\r','a'.repeat(32)+' x'])
    await assert.rejects(runReconciliation({...env,RECONCILE_KEY:key},fetcher,()=>{}),/key must be/);
  assert.equal(requests,0);
});

test('scheduler uses POST and a bounded timeout, refuses redirects and logs only aggregate counts',async()=>{
  const logs=[];
  await runReconciliation(env,async(url,options)=>{
    assert.equal(url,reconcileUrl);assert.equal(options.method,'POST');
    assert.equal(options.redirect,'error');assert.ok(options.signal instanceof AbortSignal);
    assert.deepEqual(options.headers,{'x-reconcile-key':key});assert.equal(options.body,undefined);
    return Response.json({processed:1,review:1,failed:0});
  },line=>logs.push(line));
  assert.ok(logs.some(x=>x.startsWith('::warning::')));
  assert.ok(logs.every(x=>!x.includes(key)&&!x.includes(reconcileUrl)));
});

test('scheduler fails on HTTP/network errors, hidden legacy failures and invalid summaries without leaking upstream detail',async()=>{
  for(const status of [301,401,503])
    await assert.rejects(runReconciliation(env,async()=>new Response('private upstream detail',{status}),()=>{}),new RegExp(`HTTP ${status}`));
  await assert.rejects(runReconciliation(env,async()=>{throw new Error(key);},()=>{}),/request failed/);
  for(const summary of [{processed:0,review:1},{processed:1,review:0,failed:1}])
    await assert.rejects(runReconciliation(env,async()=>Response.json(summary),()=>{}),/unresolved failures/);
  for(const summary of [{processed:-1,review:0},{processed:4,review:0},{processed:0,review:'0'},null])
    await assert.rejects(runReconciliation(env,async()=>Response.json(summary),()=>{}),/Invalid reconciliation counts/);
  await assert.rejects(runReconciliation(env,async()=>new Response('private detail'),()=>{}),/Invalid reconciliation response/);
  await runReconciliation(env,async()=>Response.json({processed:0,review:0}),()=>{});
});
