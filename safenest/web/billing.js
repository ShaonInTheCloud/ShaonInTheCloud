// Checkout visibility and prices come exclusively from the server catalogue.
export function installBilling(client,{identity,onPaid}) {
  const form=document.getElementById('billing-form'),note=document.getElementById('billing-status');
  let generation=0,products=[],busy=false;
  const say=(text,bn=text)=>{note.textContent=document.documentElement.lang==='bn'?bn:text;};
  function key(user){return 'safenest-payment:'+user;}
  function remembered(user){try{return JSON.parse(sessionStorage.getItem(key(user))||'null');}catch{return null;}}
  async function refresh() {
    const current=identity(),run=++generation;form.hidden=true;products=[];
    if(!current){say('');return;}
    say('Checkout is not open yet.','চেকআউট এখনো চালু হয়নি।');
    const {data,error}=await client.functions.invoke('payment-catalogue',{method:'GET',signal:AbortSignal.timeout(12000)});
    if(run!==generation||identity()!==current)return;
    if(!error&&data?.available===true&&Array.isArray(data.products)) {
      products=data.products.filter(p=>p.currency==='BDT'&&Number.isSafeInteger(p.amount_minor)&&p.amount_minor>=1000);
      form.elements.product_code.replaceChildren(...products.map(p=>{
        const option=document.createElement('option');option.value=p.code;
        option.textContent=`${p.plan} · ৳${(p.amount_minor/100).toFixed(2)} · ${p.duration_seconds/86400} days`;return option;
      }));
      form.hidden=products.length===0;
      say(data.environment==='sandbox'?'Sandbox test checkout — no real protection access.':'Pay securely through SSLCOMMERZ.',
        data.environment==='sandbox'?'পরীক্ষামূলক চেকআউট — আসল সুরক্ষা চালু হবে না।':'SSLCOMMERZ দিয়ে নিরাপদে পেমেন্ট করুন।');
    }
    const saved=remembered(current);
    if(saved?.order_id) {
      const {data:order,error:failure}=await client.functions.invoke('payment-status',{body:{order_id:saved.order_id},signal:AbortSignal.timeout(12000)});
      if(run!==generation||identity()!==current)return;
      if(!failure&&order?.state==='paid') {
        say(order.environment==='sandbox'?'Sandbox payment verified. Real access is unchanged.':'Payment verified. Refreshing your subscription…',
          order.environment==='sandbox'?'পরীক্ষামূলক পেমেন্ট যাচাই হয়েছে। আসল সুরক্ষা পরিবর্তন হয়নি।':'পেমেন্ট যাচাই হয়েছে। সাবস্ক্রিপশন লোড হচ্ছে…');
        sessionStorage.removeItem(key(current));if(order.environment==='live')onPaid();
      } else if(!failure&&order) {
        say('Your payment has not been confirmed. Refresh status before paying again.','পেমেন্ট এখনো নিশ্চিত হয়নি। আবার পেমেন্ট করার আগে অবস্থা যাচাই করুন।');
      }
    }
  }
  form.addEventListener('submit',async event=>{
    event.preventDefault();const current=identity();if(!current||busy)return;
    busy=true;const button=form.querySelector('button');button.disabled=true;
    try {
      const product=form.elements.product_code.value;
      if(!products.some(p=>p.code===product))throw new Error();
      let saved=remembered(current);
      if(saved&&saved.product!==product){say('Review your existing payment before choosing another plan.');return;}
      saved??={product,idempotency_key:crypto.randomUUID()};
      // Preserve the retry key across network failures and the hosted redirect.
      sessionStorage.setItem(key(current),JSON.stringify(saved));
      const customer=Object.fromEntries(['name','phone','address','city'].map(name=>[name,form.elements[name].value.trim()]));
      const {data,error}=await client.functions.invoke('payment-order',{body:{product_code:product,
        idempotency_key:saved.idempotency_key,customer},signal:AbortSignal.timeout(25000)});
      if(identity()!==current)return;
      if(error||!data?.order_id||!data?.checkout_url)throw new Error();
      const url=new URL(data.checkout_url);
      if(!['https://sandbox.sslcommerz.com','https://securepay.sslcommerz.com'].includes(url.origin)||url.username||url.password)throw new Error();
      sessionStorage.setItem(key(current),JSON.stringify({...saved,order_id:data.order_id}));
      location.assign(url.href);
    } catch {say('Checkout could not open. Retry with the same plan; if you paid, refresh status or contact support.',
      'চেকআউট খোলা যায়নি। একই প্ল্যান দিয়ে আবার চেষ্টা করুন। পেমেন্ট করলে অবস্থা যাচাই করুন বা সহায়তা নিন।');}
    finally{busy=false;button.disabled=false;}
  });
  document.getElementById('refresh-payment').addEventListener('click',()=>void refresh().catch(()=>say('Payment status is unavailable. Please try again.')));
  return {refresh:()=>void refresh().catch(()=>{if(identity())say('Checkout is currently unavailable.','চেকআউট বর্তমানে পাওয়া যাচ্ছে না।');})};
}
