// Keep off until a compatible Android build passes end-to-end trial QA.
export function installTrial(client,{identity,onStarted,releaseReady=false}) {
  const form=document.getElementById('trial-form'),button=document.getElementById('start-trial'),note=document.getElementById('trial-status');
  let busy=false;
  const say=text=>{note.textContent=text;};
  if(releaseReady){button.disabled=false;button.textContent='Start three-day trial / তিন দিনের ট্রায়াল শুরু করুন';}
  form.addEventListener('submit',async event=>{
    event.preventDefault();const user=identity();if(!releaseReady||!user||busy||!form.elements.consent.checked)return;
    busy=true;button.disabled=true;
    try {
      const {data,error}=await client.functions.invoke('start-trial',{body:{plan_code:form.elements.plan_code.value},signal:AbortSignal.timeout(15000)});
      if(identity()!==user)return;
      if(error||!data?.entitlement)throw new Error();
      if(Date.parse(data.entitlement.ends_at)<=Date.now()){say('Your trial has ended. Paid checkout is not open yet. / ট্রায়াল শেষ হয়েছে। পেইড চেকআউট এখনো বন্ধ।');return;}
      say('Trial saved. Verify access in the compatible app and follow its protection consent. No payment was authorized. / ট্রায়াল সংরক্ষিত। উপযুক্ত অ্যাপে মেয়াদ যাচাই করে সুরক্ষার সম্মতি দিন। পেমেন্টের অনুমতি দেওয়া হয়নি।');onStarted();
    }catch{if(identity()===user)say('Trial unavailable. Check your confirmed email and refresh access before retrying. / ট্রায়াল পাওয়া যাচ্ছে না। নিশ্চিত ইমেইল ও মেয়াদ যাচাই করে আবার চেষ্টা করুন।');}
    finally{busy=false;button.disabled=!releaseReady;}
  });
}
