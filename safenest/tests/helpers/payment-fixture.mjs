import {readFile,readdir} from 'node:fs/promises';
import {PGlite} from '@electric-sql/pglite';
import {validatedRecoveryArguments} from '../../supabase/functions/_shared/payments.mjs';
import {normalizeVerifiedRecovery} from '../../supabase/functions/_shared/sslcommerz.mjs';
export const buyer='11111111-1111-4111-8111-111111111111';
export const lease='22222222-2222-4222-8222-222222222222';
export const adapter={provider:'sslcommerz',environment:'sandbox'};
export const proof={orderId:buyer,provider:'sslcommerz',environment:'sandbox',status:'paid',amountMinor:1200,
  currency:'BDT',transactionId:'bankfixture',paidAt:'2026-10-07T01:00:00Z',validationReference:'valfixture',eventId:'paid',evidenceSha256:'a'.repeat(64)};
export async function fixture(db=new PGlite()) {
  await db.exec(`create role anon;create role authenticated;create role service_role bypassrls;
    create schema auth;create table auth.users(id uuid primary key);create function auth.uid() returns uuid language sql as $$select null::uuid$$;
    grant usage on schema auth,public to anon,authenticated,service_role;insert into auth.users values('${buyer}');`);
  const dir=new URL('../../supabase/migrations/',import.meta.url);
  for(const file of (await readdir(dir)).filter(n=>/protection_entitlements|payment_foundation|sslcommerz_gateway|payment_recovery/.test(n)).sort())
    await db.exec(await readFile(new URL(file,dir),'utf8'));
  await db.exec(`update payments.configuration set mode='sandbox';insert into payments.providers values('sslcommerz','sandbox',true);
    insert into payments.products values('fixture','sandbox','monthly',1200,'BDT',3600,true);set role service_role;`);
  return db;
}
export async function order(db,key='fixture-payment-order-0001') {
  return (await db.query(`select public.create_payment_order($1,'fixture','sslcommerz','sandbox',$2) result`,[buyer,key])).rows[0].result;
}
export async function evidence(db,o,changes={}) {
  const time=(await db.query('select clock_timestamp() t')).rows[0].t;
  return {...proof,orderId:o.id,paidAt:new Date(time).toISOString(),transactionId:'receipt-'+o.id,...changes};
}
export function rpc(db) {
  return async(name,args)=>{
    const values=Object.values(args);
    const placeholders=Object.keys(args).map((key,i)=>key+' => $'+(i+1)).join(',');
    return (await db.query(`select public.${name}(${placeholders}) result`,values)).rows[0].result;
  };
}
export async function recover(db,p,changes={}) {
  const e=await normalizeVerifiedRecovery(p,{kind:'refund',id:'refundfixture',state:'completed',amountMinor:1200,...changes});
  return rpc(db)('process_validated_recovery',validatedRecoveryArguments(adapter,e));
}
export async function paid(db,p) {
  const {validatedArguments}=await import('../../supabase/functions/_shared/payments.mjs');
  return rpc(db)('process_validated_payment',validatedArguments(adapter,p));
}
