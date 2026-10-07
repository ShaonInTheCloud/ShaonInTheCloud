// The database enforces confirmed identity, ownership, staff access and limits.
export function installSupportDesk(client,{identity}) {
  const $=id=>document.getElementById(id), form=$('support-form'), reply=$('support-reply-form');
  const requestedTopic=new URLSearchParams(location.search).get('topic');
  if(['app','account','billing','privacy','business'].includes(requestedTopic))form.elements.category.value=requestedTopic;
  let generation=0,currentUser=null,staff=false,selected=null,offset=0,threadLimit=100;
  let draft=null,replyDraft=null,sending=false,replying=false;
  const say=text=>{$('support-status').textContent=text;};
  const node=(tag,text)=>{const n=document.createElement(tag);n.textContent=text;return n;};
  const valid=(user,g)=>identity()===user&&g===generation;
  const categories={app:'App help / অ্যাপ সহায়তা',account:'Account / অ্যাকাউন্ট',billing:'Billing / বিলিং',privacy:'Privacy / গোপনীয়তা',business:'Business / ব্যবসা'};
  const errors={request_limit:'Request limit reached. Try again later. / অনুরোধের সীমা পূর্ণ। পরে চেষ্টা করুন।',ticket_closed:'This request is closed. / এই অনুরোধ বন্ধ।',confirmed_session_required:'Sign in with a confirmed email to send a request. / নিশ্চিত ইমেইল দিয়ে লগইন করুন।'};
  function failed(error){say(Object.entries(errors).find(([key])=>error?.message?.includes(key))?.[1]||'Could not complete this request. Your text is kept; refresh before retrying. / কাজটি সম্পন্ন হয়নি। লেখা রাখা আছে; আবার চেষ্টার আগে রিফ্রেশ করুন।');}
  async function list(append=false){
    const user=identity(),g=generation;if(!user)return;
    if(!append)offset=0;
    try{
      let q=client.from('support_tickets').select('id,category,subject,status,updated_at').order('updated_at',{ascending:false}).order('id').range(offset,offset+49);
      if(!staff)q=q.eq('user_id',user);
      const {data,error}=await q.abortSignal(AbortSignal.timeout(12000));if(!valid(user,g))return;if(error)throw error;
      if(!append)$('support-list').replaceChildren();
      for(const t of data){const b=node('button',`${t.subject} · ${categories[t.category]} · ${t.status==='open'?'Open / খোলা':'Closed / বন্ধ'}`);b.type='button';b.className='support-ticket';b.addEventListener('click',()=>{selected=t;threadLimit=100;void thread();});$('support-list').append(b);}
      offset+=data.length;$('support-more').hidden=data.length<50;
      if(offset===0)$('support-list').append(node('p','No requests yet. / এখনো কোনো অনুরোধ নেই।'));
    }catch(error){if(valid(user,g))failed(error);}
  }
  async function thread(){
    const user=identity(),g=generation,t=selected;if(!user||!t)return;
    try{
      const {data,error}=await client.from('support_messages').select('id,body,is_support,created_at').eq('ticket_id',t.id).order('created_at',{ascending:false}).order('id').limit(threadLimit).abortSignal(AbortSignal.timeout(12000));
      if(!valid(user,g)||selected?.id!==t.id)return;if(error)throw error;
      $('support-thread').hidden=false;$('support-thread-title').textContent=t.subject;
      $('support-reference').textContent=`Reference / রেফারেন্স: ${t.id}`;
      $('support-messages').replaceChildren();
      for(const m of [...data].reverse()){const article=document.createElement('article');article.className='support-message';article.append(node('p',(m.is_support?'SafeNest support / SafeNest সহায়তা':'Customer / গ্রাহক')+' · '+new Date(m.created_at).toLocaleString()),node('p',m.body));$('support-messages').append(article);}
      $('support-earlier').hidden=data.length<threadLimit;
      reply.hidden=t.status!=='open';$('support-change-status').hidden=!staff;
      $('support-change-status').textContent=t.status==='open'?'Close request / অনুরোধ বন্ধ করুন':'Reopen request / আবার খুলুন';
    }catch(error){if(valid(user,g)&&selected?.id===t.id)failed(error);}
  }
  async function refresh(){
    const user=identity();
    if(user!==currentUser){generation++;currentUser=user;staff=false;selected=null;draft=null;replyDraft=null;form.reset();if(['app','account','billing','privacy','business'].includes(requestedTopic))form.elements.category.value=requestedTopic;reply.reset();$('support-list').replaceChildren();$('support-messages').replaceChildren();$('support-thread').hidden=true;$('support-more').hidden=true;$('support-operator-note').hidden=true;say('');}
    if(!user)return;
    const g=++generation;
    try{const {data,error}=await client.rpc('support_is_operator').abortSignal(AbortSignal.timeout(12000));if(!valid(user,g))return;if(error)throw error;staff=data===true;$('support-operator-note').hidden=!staff;
      await list();if(selected){const {data:t,error:e}=await client.from('support_tickets').select('id,category,subject,status').eq('id',selected.id).maybeSingle().abortSignal(AbortSignal.timeout(12000));if(!valid(user,g))return;if(e)throw e;if(t){selected=t;await thread();}else{selected=null;$('support-thread').hidden=true;}}
    }catch(error){if(valid(user,g))failed(error);}
  }
  form.addEventListener('submit',async event=>{
    event.preventDefault();const user=identity(),g=generation;if(!user||sending||!form.reportValidity())return;
    const values=Object.fromEntries(new FormData(form)),fingerprint=JSON.stringify(values);
    if(draft?.fingerprint!==fingerprint)draft={fingerprint,id:crypto.randomUUID(),ticket:crypto.randomUUID()};
    const submission=draft;sending=true;form.querySelector('button').disabled=true;say('Saving… / সংরক্ষণ হচ্ছে…');
    try{const {data,error}=await client.rpc('support_send',{p_id:submission.id,p_ticket:submission.ticket,p_body:values.message,p_category:values.category,p_subject:values.subject}).abortSignal(AbortSignal.timeout(12000));if(error)throw error;if(!valid(user,g))return;
      if(data!==submission.ticket)throw Error('invalid_response');form.reset();draft=null;say('Request saved. Replies appear here; email notifications are not active. / অনুরোধ রাখা হয়েছে। উত্তর এখানে আসবে; ইমেইল নোটিফিকেশন চালু নয়।');await list();
    }catch(error){if(valid(user,g))failed(error);}finally{sending=false;form.querySelector('button').disabled=false;}
  });
  reply.addEventListener('submit',async event=>{
    event.preventDefault();const user=identity(),g=generation,t=selected;if(!user||!t||replying||!reply.reportValidity())return;
    const body=new FormData(reply).get('message'),fingerprint=JSON.stringify([t.id,body]);
    if(replyDraft?.fingerprint!==fingerprint)replyDraft={fingerprint,id:crypto.randomUUID()};
    const submission=replyDraft;replying=true;reply.querySelector('button').disabled=true;
    try{const {error}=await client.rpc('support_send',{p_id:submission.id,p_ticket:t.id,p_body:body}).abortSignal(AbortSignal.timeout(12000));if(error)throw error;if(!valid(user,g)||selected?.id!==t.id)return;reply.reset();replyDraft=null;say('Reply saved. / উত্তর রাখা হয়েছে।');await thread();await list();}
    catch(error){if(valid(user,g))failed(error);}finally{replying=false;reply.querySelector('button').disabled=false;}
  });
  $('support-refresh').addEventListener('click',()=>void refresh());
  $('support-more').addEventListener('click',()=>void list(true));
  $('support-earlier').addEventListener('click',()=>{threadLimit+=100;void thread();});
  $('support-change-status').addEventListener('click',async()=>{const user=identity(),g=generation,t=selected;if(!user||!staff||!t)return;try{const {error}=await client.rpc('support_set_status',{p_ticket:t.id,p_status:t.status==='open'?'closed':'open'}).abortSignal(AbortSignal.timeout(12000));if(error)throw error;if(valid(user,g))await refresh();}catch(error){if(valid(user,g))failed(error);}});
  return {refresh};
}
