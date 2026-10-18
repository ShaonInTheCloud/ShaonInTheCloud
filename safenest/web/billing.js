// Checkout visibility, prices and payment methods come exclusively from the server catalogue.
// The browser never decides whether a provider is open; it only chooses among what the server offers.
const knownOrigins={
  sslcommerz:['https://sandbox.sslcommerz.com','https://securepay.sslcommerz.com'],
  stripe:['https://checkout.stripe.com'],
  nowpayments:['https://sandbox.nowpayments.io','https://nowpayments.io'],
};
const symbols={BDT:'৳',USD:'$',EUR:'€'};
const planText={weekly:['Weekly','সাপ্তাহিক'],monthly:['Monthly','মাসিক'],quarterly:['Three months','তিন মাস'],annual:['Yearly','বার্ষিক']};
export function formatPrice(product) {
  const symbol=symbols[product.currency]??product.currency+' ';
  const amount=(product.amount_minor/100).toFixed(2).replace(/\.00$/,'');
  return `${symbol}${amount}`;
}
export function installBilling(client,{identity,onPaid}) {
  const form=document.getElementById('billing-form'),note=document.getElementById('billing-status');
  let generation=0,products=[],providers=[],busy=false;
  const bn=()=>document.documentElement.lang==='bn';
  const say=(text,bnText=text)=>{note.textContent=bn()?bnText:text;};
  function key(user){return 'safenest-payment:'+user;}
  function remembered(user){try{return JSON.parse(sessionStorage.getItem(key(user))||'null');}catch{return null;}}
  const region=()=>form.elements.region.value;
  const method=()=>form.elements.provider.value;
  function visibleProducts(){return products.filter(p=>p.region===region());}
  function selectedProduct(){return products.find(p=>p.code===form.elements.product_code.value);}
  function renderProducts() {
    const list=visibleProducts();
    form.elements.product_code.replaceChildren(...list.map(p=>{
      const option=document.createElement('option');option.value=p.code;
      const [en,bnLabel]=planText[p.plan]??[p.plan,p.plan];
      option.textContent=`${bn()?bnLabel:en} · ${formatPrice(p)} · ${p.duration_seconds/86400} ${bn()?'দিন':'days'}`;return option;
    }));
    renderMethods();
  }
  function renderMethods() {
    const product=selectedProduct();
    const allowed=new Set(product?.providers??[]);
    let firstVisible=null;
    for (const label of form.querySelectorAll('.billing-method label[data-provider]')) {
      const name=label.dataset.provider,input=label.querySelector('input');
      const show=allowed.has(name)&&providers.some(p=>p.provider===name);
      label.hidden=!show;input.disabled=!show;
      if(show&&!firstVisible)firstVisible=input;
      if(!show)input.checked=false;
    }
    if(firstVisible&&!form.querySelector('.billing-method input:checked'))firstVisible.checked=true;
    renderDetails();
  }
  function renderDetails() {
    const needsDetails=method()==='sslcommerz';
    const box=form.querySelector('.billing-details');
    box.hidden=!needsDetails;
    for(const input of box.querySelectorAll('input'))input.required=needsDetails;
  }
  for(const radio of form.querySelectorAll('.billing-region input'))radio.addEventListener('change',renderProducts);
  form.elements.product_code.addEventListener('change',renderMethods);
  for(const radio of form.querySelectorAll('.billing-method input'))radio.addEventListener('change',renderDetails);
  async function refresh() {
    const current=identity(),run=++generation;form.hidden=true;products=[];providers=[];
    if(!current){say('');return;}
    say('Checkout is not open yet.','চেকআউট এখনো চালু হয়নি।');
    const {data,error}=await client.functions.invoke('payment-catalogue',{method:'GET',signal:AbortSignal.timeout(12000)});
    if(run!==generation||identity()!==current)return;
    if(!error&&data?.available===true&&Array.isArray(data.products)&&Array.isArray(data.providers)) {
      providers=data.providers.filter(p=>knownOrigins[p.provider]&&Array.isArray(p.checkout_origins)
        &&p.checkout_origins.every(origin=>knownOrigins[p.provider].includes(origin)));
      const offered=new Set(providers.map(p=>p.provider));
      products=data.products.filter(p=>['bd','global'].includes(p.region)&&/^[A-Z]{3}$/.test(p.currency??'')
        &&Number.isSafeInteger(p.amount_minor)&&p.amount_minor>=50&&Array.isArray(p.providers)&&p.providers.some(name=>offered.has(name)));
      const regions=new Set(products.map(p=>p.region));
      for(const radio of form.querySelectorAll('.billing-region input')){radio.disabled=!regions.has(radio.value);radio.closest('label').hidden=!regions.has(radio.value);}
      if(!regions.has(region())&&regions.size)form.querySelector(`.billing-region input[value="${[...regions][0]}"]`).checked=true;
      renderProducts();
      form.hidden=products.length===0;
      say(data.environment==='sandbox'?'Sandbox test checkout — no real protection access.':'Choose a plan and a payment method. You pay on the provider’s secure page.',
        data.environment==='sandbox'?'পরীক্ষামূলক চেকআউট — আসল সুরক্ষা চালু হবে না।':'প্ল্যান ও পেমেন্ট মাধ্যম বেছে নিন। পেমেন্ট হবে সেবা-প্রদানকারীর নিরাপদ পেজে।');
    }
    const saved=remembered(current);
    if(saved?.order_id) {
      const {data:order,error:failure}=await client.functions.invoke('payment-status',{body:{order_id:saved.order_id},signal:AbortSignal.timeout(12000)});
      if(run!==generation||identity()!==current)return;
      if(!failure&&order?.state==='paid') {
        say(order.environment==='sandbox'?'Sandbox payment verified. Real access is unchanged.':'Payment verified. Refreshing your subscription…',
          order.environment==='sandbox'?'পরীক্ষামূলক পেমেন্ট যাচাই হয়েছে। আসল সুরক্ষা পরিবর্তন হয়নি।':'পেমেন্ট যাচাই হয়েছে। সাবস্ক্রিপশন লোড হচ্ছে…');
        sessionStorage.removeItem(key(current));if(order.environment==='live')onPaid();
      } else if(!failure&&order&&['cancelled','failed'].includes(order.state)) {
        say('That payment did not complete. You can start a new one.','ওই পেমেন্ট সম্পন্ন হয়নি। নতুন করে শুরু করতে পারেন।');
        sessionStorage.removeItem(key(current));
      } else if(!failure&&order) {
        say(saved.provider==='nowpayments'?'Your crypto payment is not confirmed yet. Confirmations can take a while; refresh status before paying again.'
          :'Your payment has not been confirmed. Refresh status before paying again.',
          saved.provider==='nowpayments'?'ক্রিপ্টো পেমেন্ট এখনো নিশ্চিত হয়নি। নিশ্চিত হতে কিছুটা সময় লাগতে পারে; আবার পেমেন্টের আগে অবস্থা যাচাই করুন।'
          :'পেমেন্ট এখনো নিশ্চিত হয়নি। আবার পেমেন্ট করার আগে অবস্থা যাচাই করুন।');
      }
    }
  }
  form.addEventListener('submit',async event=>{
    event.preventDefault();const current=identity();if(!current||busy)return;
    busy=true;const button=form.querySelector('button[type="submit"]');button.disabled=true;
    try {
      const product=form.elements.product_code.value,provider=method();
      const chosen=products.find(p=>p.code===product);
      if(!chosen||!chosen.providers.includes(provider)||!providers.some(p=>p.provider===provider))throw new Error();
      let saved=remembered(current);
      if(saved&&(saved.product!==product||saved.provider!==provider)){say('Review your existing payment before choosing another plan or method.','অন্য প্ল্যান বা মাধ্যম বেছে নেওয়ার আগে চলমান পেমেন্টটি যাচাই করুন।');return;}
      saved??={product,provider,idempotency_key:crypto.randomUUID()};
      // Preserve the retry key across network failures and the hosted redirect.
      sessionStorage.setItem(key(current),JSON.stringify(saved));
      const customer=provider==='sslcommerz'
        ?Object.fromEntries(['name','phone','address','city'].map(name=>[name,form.elements[name].value.trim()])):{};
      const {data,error}=await client.functions.invoke('payment-order',{body:{product_code:product,provider,
        idempotency_key:saved.idempotency_key,customer},signal:AbortSignal.timeout(25000)});
      if(identity()!==current)return;
      if(error||!data?.order_id||!data?.checkout_url)throw new Error();
      const url=new URL(data.checkout_url);
      if(url.protocol!=='https:'||!knownOrigins[provider].includes(url.origin)||url.username||url.password)throw new Error();
      sessionStorage.setItem(key(current),JSON.stringify({...saved,order_id:data.order_id}));
      location.assign(url.href);
    } catch {say('Checkout could not open. Retry with the same plan; if you paid, refresh status or contact support.',
      'চেকআউট খোলা যায়নি। একই প্ল্যান দিয়ে আবার চেষ্টা করুন। পেমেন্ট করলে অবস্থা যাচাই করুন বা সহায়তা নিন।');}
    finally{busy=false;button.disabled=false;}
  });
  document.getElementById('refresh-payment').addEventListener('click',()=>void refresh().catch(()=>say('Payment status is unavailable. Please try again.')));
  return {refresh:()=>void refresh().catch(()=>{if(identity())say('Checkout is currently unavailable.','চেকআউট বর্তমানে পাওয়া যাচ্ছে না।');})};
}
