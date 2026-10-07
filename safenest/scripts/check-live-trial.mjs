#!/usr/bin/env node
// Two-phase live API acceptance. Uses only a disposable account's credentials,
// never a service key or paid grant. Real Android UI/device evidence is separate.
import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';

const phase = process.argv[2];
const reportPath = process.argv[3];
assert.ok(['start','expired-cleanup'].includes(phase) && reportPath,
  'Usage: node scripts/check-live-trial.mjs start|expired-cleanup REPORT.json');
const base = 'https://kflenmeizngmafwnwhgv.supabase.co';
const key = 'sb_publishable_jt2VeNCAATz3iEiebx2Kog_ZiZVL7Vl';
const email = process.env.SAFENEST_TRIAL_QA_EMAIL;
const password = process.env.SAFENEST_TRIAL_QA_PASSWORD;
const plan = process.env.SAFENEST_TRIAL_QA_PLAN;
assert.match(email ?? '', /\+safenest-trial-[a-zA-Z0-9-]+@gmail\.com$/,
  'Use an owned disposable safenest-trial email alias');
assert.ok(password && ['monthly','quarterly','annual'].includes(plan));
assert.equal(process.env.SAFENEST_TRIAL_QA_CONSENT,'72-hour-disposable-test',
  'Explicit QA trial consent must be supplied');
let token;
const report = phase === 'start' ? {scope:'live API; Android acceptance remains pending',
  phase,started_at:new Date().toISOString(),checks:[],acceptance_passed:false} :
  JSON.parse(await readFile(reportPath,'utf8'));
async function call(path,body,jwt=token) {
  const response = await fetch(base+path,{method:'POST',redirect:'error',signal:AbortSignal.timeout(20000),
    headers:{apikey:key,'Content-Type':'application/json',Origin:'https://mysafenestbd.com',...(jwt?{Authorization:'Bearer '+jwt}:{})},
    body:JSON.stringify(body)});
  let data; try { data = await response.json(); } catch { data = {}; }
  return {status:response.status,data};
}
const pass = name => {report.checks.push(name);console.log('PASS '+name);};
try {
  const login = await call('/auth/v1/token?grant_type=password',{email,password},null);
  assert.equal(login.status,200,'Confirmed password login failed');
  assert.ok(login.data.user?.email_confirmed_at && !login.data.user.is_anonymous);
  token = login.data.access_token;
  assert.ok(token);
  if (phase === 'start') {
    pass('confirmed password login');
    const before = await call('/functions/v1/protection-access',{});
    assert.equal(before.status,200);assert.equal(before.data.active,false);
    pass('login/access lookup does not grant protection');
    const forged = await call('/functions/v1/start-trial',{plan_code:plan,ends_at:'2099-01-01',paid:true});
    assert.equal(forged.status,400);
    pass('client-selected end time/paid flag rejected');
    const first = await call('/functions/v1/start-trial',{plan_code:plan});
    assert.equal(first.status,200);assert.equal(first.data.already_claimed,false);
    const grant = first.data.entitlement;
    assert.equal(grant.plan_code,'trial');assert.equal(grant.provider,'safenest-trial');
    assert.equal(grant.user_id,login.data.user.id);
    assert.equal(Date.parse(grant.ends_at)-Date.parse(grant.starts_at),72*3600000);
    assert.equal(first.data.selected_plan_code,plan);assert.equal(first.data.automatic_charging,false);
    report.entitlement_id=grant.id;report.starts_at=grant.starts_at;report.ends_at=grant.ends_at;
    report.selected_plan_code=plan;
    // Persist immediately so a later failure cannot lose the issued trial's identity.
    await writeFile(reportPath,JSON.stringify(report,null,2)+'\n',{mode:0o600});
    pass('one-time server-issued 72-hour trial with selected plan; charging false');
    for (const retryPlan of [plan,plan==='annual'?'monthly':'annual']) {
      const retry = await call('/functions/v1/start-trial',{plan_code:retryPlan});
      assert.equal(retry.status,200);assert.equal(retry.data.already_claimed,true);
      assert.equal(retry.data.entitlement.id,grant.id);assert.equal(retry.data.entitlement.ends_at,grant.ends_at);
      assert.equal(retry.data.selected_plan_code,plan);
    }
    pass('retries preserve original entitlement, plan and exact end time');
    const active = await call('/functions/v1/protection-access',{entitlement_id:grant.id});
    assert.equal(active.status,200);assert.equal(active.data.active,true);
    assert.equal(active.data.checked_entitlement_id,grant.id);
    pass('authenticated access verifies the same active trial');
  } else {
    assert.ok(report.entitlement_id && report.ends_at,'Missing start-phase evidence');
    const fixture = process.env.SAFENEST_TRIAL_QA_EXPIRY_MODE === 'forced-disposable-fixture';
    if (!fixture) assert.ok(Date.now()>=Date.parse(report.ends_at),'Real 72-hour end has not arrived');
    report.expiry_mode = fixture ? 'forced disposable backend fixture; not natural 72-hour/device expiry' : 'natural expiry';
    const access = await call('/functions/v1/protection-access',{entitlement_id:report.entitlement_id});
    assert.equal(access.status,200);assert.equal(access.data.active,false);
    pass('expired trial no longer grants server access');
    const retry = await call('/functions/v1/start-trial',{plan_code:plan});
    assert.equal(retry.status,200);assert.equal(retry.data.already_claimed,true);
    assert.equal(retry.data.entitlement.id,report.entitlement_id);
    assert.ok(Date.parse(retry.data.entitlement.ends_at)<=Date.parse(access.data.server_now));
    if (!fixture) assert.equal(retry.data.entitlement.ends_at,report.ends_at);
    pass('repeat start returns only the spent claim; no fresh active trial');
    const deleted = await call('/functions/v1/delete-account',{password,confirm:true});
    assert.equal(deleted.status,200,'Disposable account cleanup failed');
    const denied = await call('/auth/v1/token?grant_type=password',{email,password},null);
    assert.notEqual(denied.status,200);
    pass('disposable account deleted and password login denied');
    report.cleaned_up_at=new Date().toISOString();
  }
} catch {
  report.failure='Acceptance assertion or API call failed; credentials and responses suppressed';
  console.error(report.failure);
  process.exitCode=1;
} finally {
  // Reports contain no email, password, JWT, refresh token or API error body.
  await writeFile(reportPath,JSON.stringify(report,null,2)+'\n',{mode:0o600});
}
