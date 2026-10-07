import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
import {randomUUID} from 'node:crypto';

test('support requests are private, retry-safe, bounded and require a live confirmed session; only operators reply across accounts',async()=>{
 const db=new PGlite(),a=randomUUID(),b=randomUUID(),operator=randomUUID(),unconfirmed=randomUUID();
 const sessions=new Map([a,b,operator,unconfirmed].map(id=>[id,randomUUID()]));
 try{
  await db.exec(`create role anon;create role authenticated;create role service_role bypassrls;create schema auth;
   create table auth.users(id uuid primary key,email text,email_confirmed_at timestamptz,is_anonymous boolean default false);
   create table auth.sessions(id uuid primary key,user_id uuid references auth.users(id) on delete cascade,not_after timestamptz);
   create function auth.jwt() returns jsonb language sql stable as $$select current_setting('request.jwt.claims',true)::jsonb$$;
   create function auth.uid() returns uuid language sql stable as $$select (auth.jwt()->>'sub')::uuid$$;
   grant usage on schema auth,public to anon,authenticated;`);
  for(const id of sessions.keys()){await db.query('insert into auth.users values($1,$2,$3,false)',[id,id===operator?'operator@example.test':id+'@example.test',id===unconfirmed?null:new Date().toISOString()]);await db.query('insert into auth.sessions values($1,$2,null)',[sessions.get(id),id]);}
  await db.exec(await readFile(new URL('../supabase/migrations/20261007004620_support_desk.sql',import.meta.url),'utf8'));
  await db.exec("insert into supportdesk.operators values('operator@example.test')");
  const as=async(id,role='authenticated')=>{await db.exec('reset role');await db.query("select set_config('request.jwt.claims',$1,false)",[JSON.stringify({sub:id,session_id:sessions.get(id),user_metadata:{safenest_admin:true}})]);await db.exec('set role '+role);};
  const send=async(ticket=randomUUID(),message=randomUUID())=>(await db.query("select public.support_send($1,$2,$3,'app','DNS help') id",[message,ticket,'A normal website will not load.'])).rows[0].id;
  await as(a);const ticket=randomUUID(),message=randomUUID();assert.equal(await send(ticket,message),ticket);assert.equal(await send(ticket,message),ticket);
  assert.equal((await db.query('select * from public.support_messages')).rows.length,1);
  assert.equal((await db.query('select public.support_is_operator() ok')).rows[0].ok,false);
  await assert.rejects(db.query('update public.support_tickets set status=\'closed\''),{code:'42501'});
  await assert.rejects(db.query('insert into supportdesk.operators values(\'attacker@example.test\')'),{code:'42501'});
  await assert.rejects(db.query('insert into public.support_messages(id,ticket_id,author_id,is_support,body) values($1,$2,$3,true,$4)',[randomUUID(),ticket,a,'Forged support reply']),{code:'42501'});
  await as(b);assert.equal((await db.query('select * from public.support_tickets')).rows.length,0);assert.equal((await db.query('select * from public.support_messages')).rows.length,0);
  assert.equal((await db.query('select * from public.support_messages where ticket_id=$1',[ticket])).rows.length,0);
  await assert.rejects(send(ticket),/ticket_unavailable/);await assert.rejects(db.query("select public.support_set_status($1,'closed')",[ticket]),/operator_required/);
  await as(operator);assert.equal((await db.query('select public.support_is_operator() ok')).rows[0].ok,true);assert.equal((await db.query('select * from public.support_tickets')).rows.length,1);
  await send(ticket);assert.equal((await db.query('select * from public.support_messages where is_support')).rows.length,1);
  await db.query("select public.support_set_status($1,'closed')",[ticket]);await as(a);await assert.rejects(send(ticket),/ticket_closed/);assert.equal(await send(ticket,message),ticket);
  await as(operator);await db.query("select public.support_set_status($1,'open')",[ticket]);await as(a);
  await assert.rejects(db.query("select public.support_send($1,$2,'short','app','Test')",[randomUUID(),randomUUID()]),/invalid_message/);
  for(let i=0;i<4;i++)await send();await assert.rejects(send(),/request_limit/);
  await as(unconfirmed);await assert.rejects(send(),/confirmed_session_required/);
  await as(a,'anon');await assert.rejects(db.query('select * from public.support_tickets'),{code:'42501'});await assert.rejects(send(),{code:'42501'});
  await db.exec('reset role');await db.query('delete from auth.sessions where user_id=$1',[a]);await as(a);assert.equal((await db.query('select * from public.support_tickets')).rows.length,0);await assert.rejects(send(),/confirmed_session_required/);
  await db.exec('reset role');await db.query('delete from auth.users where id=$1',[a]);assert.equal((await db.query('select * from public.support_tickets')).rows.length,0);assert.equal((await db.query('select * from public.support_messages')).rows.length,0);
 }finally{await db.close();}
});
