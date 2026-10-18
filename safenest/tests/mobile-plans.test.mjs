import test, {after, afterEach} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import React from 'react';
import {act, create} from 'react-test-renderer';
import {build} from 'esbuild';

// Exercise the real screen, translations and API client. Only native UI, focus,
// session storage and the browser are replaced; all HTTP responses are fixtures.
const require=createRequire(import.meta.url);
const directory=await mkdtemp(join(tmpdir(),'safenest-plans-'));
const file=join(directory,'plans.mjs');
await build({stdin:{contents:`export {default as Plans} from './mobile/src/app/(tabs)/plans.tsx';
  export {strings} from './mobile/src/lib/i18n.ts';`,resolveDir:new URL('..',import.meta.url).pathname},
  outfile:file,bundle:true,platform:'node',format:'esm',jsx:'automatic',plugins:[{
    name:'native-test-boundaries',setup(builder) {
      builder.onResolve({filter:/^react(?:\/jsx-runtime)?$/},args=>({path:require.resolve(args.path),external:true}));
      builder.onResolve({filter:/^(react-native|expo-router|expo-web-browser|@\/components\/ui|@\/lib\/store)$/},args=>({path:args.path,namespace:'fixture'}));
      builder.onResolve({filter:/^\.\/supabase$/},args=>args.importer.endsWith('/lib/api.ts')?{path:'supabase',namespace:'fixture'}:undefined);
      builder.onLoad({filter:/.*/,namespace:'fixture'},args=>({contents:args.path==='react-native' ? `export const View='View';`
        : args.path==='expo-router' ? `import {useEffect} from 'react'; export const useFocusEffect=cb=>useEffect(cb,[cb]);`
        : args.path==='expo-web-browser' ? `export const openBrowserAsync=(...args)=>globalThis.__plans.browser(...args);`
        : args.path==='@/lib/store' ? `export const useStore=()=>globalThis.__plans.store;`
        : args.path==='supabase' ? `export const SUPABASE_URL='https://fixture.invalid', SUPABASE_KEY='public-fixture';
            export const supabase={auth:{getSession:async()=>({data:{session:{access_token:'fixture.jwt'}}})}};`
        : `export const Body='Body', Button='Button', Card='Card', Display='Display', Label='Label', Notice='Notice', Pill='Pill', Screen='Screen', Title='Title';`,
        loader:'js'}));
    },
  }]});
const {Plans,strings}=await import(pathToFileURL(file).href);
const originalFetch=globalThis.fetch;
const originalStorage=Object.getOwnPropertyDescriptor(globalThis,'localStorage');
globalThis.IS_REACT_ACT_ENVIRONMENT=true;
let renderer, requests, saved;
const provider={provider:'stripe',checkout_origins:['https://checkout.stripe.com']};
const product={code:'monthly_v2',plan:'monthly',region:'bd',amount_minor:37900,currency:'BDT',duration_seconds:2592000,providers:['stripe']};
const ready={available:true,environment:'sandbox',products:[product],providers:[provider]};
const closed={available:false,products:[],providers:[]};
const deferred=()=>{let resolve;const promise=new Promise(r=>{resolve=r;});return {promise,resolve};};

function setup(lang='en',catalogue=async()=>Response.json(closed),status=async()=>Response.json({state:'pending'})) {
  requests=[];saved=new Map();
  Object.defineProperty(globalThis,'localStorage',{configurable:true,value:{getItem:key=>saved.get(key)??null,
    setItem:(key,value)=>saved.set(key,value),removeItem:key=>saved.delete(key)}});
  globalThis.__plans={store:{t:strings[lang],lang,edition:'bd',session:{user:{id:'owner'}},testMode:false,setTestPlan:()=>{}},browser:async()=>{}};
  globalThis.fetch=async(url,init)=>{
    requests.push({url,init});
    if(url.endsWith('/payment-catalogue')) return catalogue();
    if(url.endsWith('/payment-status')) return status();
    if(url.endsWith('/payment-order')) return Response.json({order_id:'order-fixture',checkout_url:'https://checkout.stripe.com/session'});
    throw new Error('Unexpected request: '+url);
  };
}
async function mount(){await act(async()=>{renderer=create(React.createElement(Plans));});}
const notices=()=>renderer.root.findAllByType('Notice').map(n=>n.props.text);
const buttons=()=>renderer.root.findAllByType('Button');
const cards=()=>renderer.root.findAllByType('Card');
async function press(label){await act(async()=>{await buttons().find(b=>b.props.label===label).props.onPress();});}
function noPurchase(){assert.equal(requests.some(r=>r.url.endsWith('/payment-order')),false);}
afterEach(async()=>{if(renderer){await act(async()=>renderer.unmount());renderer=null;}globalThis.fetch=originalFetch;});
after(async()=>{
  if(originalStorage) Object.defineProperty(globalThis,'localStorage',originalStorage);else delete globalThis.localStorage;
  delete globalThis.__plans;delete globalThis.IS_REACT_ACT_ENVIRONMENT;
  await rm(directory,{recursive:true,force:true});
});

for(const lang of ['en','bn']) {
  test(`${lang}: loading is not disabled checkout and cannot start a payment`,async()=>{
    const pending=deferred();setup(lang,()=>pending.promise);await mount();
    assert.ok(notices().includes(strings[lang].plansLoading));
    assert.ok(!notices().includes(strings[lang].plansClosed));assert.equal(cards().length,0);
    assert.ok(buttons().every(b=>b.props.disabled));noPurchase();
    await act(async()=>{pending.resolve(Response.json(closed));});
    assert.ok(notices().includes(strings[lang].plansClosed));
  });

  test(`${lang}: successful disabled checkout alone shows proposed prices without payment buttons`,async()=>{
    setup(lang);await mount();
    assert.ok(notices().includes(strings[lang].plansClosed));assert.equal(cards().length,3);
    assert.deepEqual(buttons().map(b=>b.props.label),[strings[lang].refreshStatus]);noPurchase();
  });

  for(const empty of [{...ready,available:false,products:[]},{...ready,products:[{...product,region:'global'}]}]) {
    test(`${lang}: empty catalogue/edition explains no plans without proposed-price fallback`,async()=>{
      setup(lang,async()=>Response.json(empty));await mount();
      assert.ok(notices().includes(strings[lang].plansEmpty));assert.ok(!notices().includes(strings[lang].plansClosed));
      assert.equal(cards().length,0);noPurchase();
    });
  }

  for(const failure of ['network','timeout','backend','invalid-json','invalid-shape']) {
    test(`${lang}: ${failure} has a useful message and retry restores server-confirmed availability`,async()=>{
      let tries=0;
      setup(lang,async()=>{
        if(tries++) return Response.json(ready);
        if(failure==='network') throw new TypeError('Network request failed');
        if(failure==='timeout') throw new DOMException('Aborted','AbortError');
        if(failure==='backend') return Response.json({error:'private internal detail'},{status:503});
        if(failure==='invalid-json') return new Response('not JSON');
        return Response.json({});
      });await mount();
      const expected=['network','timeout'].includes(failure)?strings[lang].plansNetworkError:strings[lang].plansServerError;
      assert.ok(notices().includes(expected));assert.ok(!notices().includes(strings[lang].plansClosed));
      assert.equal(cards().length,0);noPurchase();
      await press(strings[lang].plansRetry);
      assert.equal(tries,2);assert.ok(!notices().includes(expected));
      assert.ok(notices().includes(strings[lang].plansSandbox));assert.equal(cards().length,1);
      assert.ok(buttons().some(b=>b.props.label===strings[lang].payCard));noPurchase();
    });
  }
}

test('refresh clears stale purchasable prices and a failed refresh offers retry',async()=>{
  const pending=deferred();let calls=0;setup('en',()=>++calls===1?Response.json(ready):pending.promise);await mount();
  let refresh;
  await act(async()=>{refresh=buttons().find(b=>b.props.label===strings.en.refreshStatus).props.onPress();});
  assert.equal(cards().length,0);assert.ok(notices().includes(strings.en.plansLoading));
  await act(async()=>{pending.resolve(Response.json({error:'unavailable'},{status:503}));await refresh;});
  assert.ok(notices().includes(strings.en.plansServerError));assert.equal(cards().length,0);noPurchase();
});

test('an older request cannot overwrite a successful retry after language/focus changes',async()=>{
  const pending=deferred();let calls=0;setup('en',()=>++calls===1?pending.promise:Response.json(ready));await mount();
  globalThis.__plans.store={...globalThis.__plans.store,lang:'bn',t:strings.bn};
  await act(async()=>{renderer.update(React.createElement(Plans));});
  await act(async()=>{pending.resolve(Response.json({error:'late failure'},{status:503}));});
  assert.equal(cards().length,1);assert.ok(notices().includes(strings.bn.plansSandbox));
  assert.ok(!notices().includes(strings.bn.plansServerError));
});

for(const state of ['paid','pending','cancelled','failed','status-error']) {
  test(`catalogue failure still recovers a ${state} saved payment`,async()=>{
    setup('bn',async()=>{throw new TypeError('offline');},async()=>Response.json({state},{status:state==='status-error'?503:200}));
    saved.set('safenest.payment.owner',JSON.stringify({order_id:'saved-order'}));await mount();
    assert.ok(notices().includes(strings.bn.plansNetworkError));
    assert.equal(requests.filter(r=>r.url.endsWith('/payment-status')).length,1);
    assert.equal(saved.has('safenest.payment.owner'),!['paid','cancelled','failed'].includes(state));
    if(state==='paid') assert.ok(notices().includes(strings.bn.paymentDone));
    if(state==='pending') assert.ok(notices().includes(strings.bn.paymentPending));
    noPurchase();
  });
}

test('a retry can confirm disabled checkout without enabling charging',async()=>{
  let calls=0;setup('en',async()=>++calls===1?Response.json({error:'unavailable'},{status:503}):Response.json(closed));
  await mount();await press(strings.en.plansRetry);
  assert.ok(notices().includes(strings.en.plansClosed));assert.equal(cards().length,3);
  assert.deepEqual(buttons().map(b=>b.props.label),[strings.en.refreshStatus]);noPurchase();
});

test('test guest keeps simulated payments separate from a failed real catalogue',async()=>{
  setup('en',async()=>{throw new TypeError('offline');});
  globalThis.__plans.store={...globalThis.__plans.store,testMode:true,session:null};await mount();
  assert.equal(cards().length,3);assert.ok(buttons().some(b=>b.props.label===strings.en.payCard));
  assert.ok(!notices().includes(strings.en.plansClosed));noPurchase();
});

test('hosted checkout still rejects a URL outside the HTTPS provider allowlist',async()=>{
  setup('en',async()=>Response.json(ready));const fetcher=globalThis.fetch;
  globalThis.fetch=(url,init)=>url.endsWith('/payment-order')
    ?Response.json({order_id:'order-fixture',checkout_url:'https://checkout.stripe.com.evil.invalid/session'}):fetcher(url,init);
  let opened=0;globalThis.__plans.browser=async()=>{opened++;};await mount();await press(strings.en.payCard);
  assert.equal(opened,0);assert.ok(notices().includes(strings.en.errorGeneric));
});
