import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';

// Execute the actual workflow step with a shell function replacing curl. No network
// or production secrets are used, including for the configured happy path.
const workflow=readFileSync(new URL('../../.github/workflows/safenest-payment-reconcile.yml',import.meta.url),'utf8');
const script=workflow.split('        run: |\n')[1].split('\n').map(line=>line.replace(/^          /,'')).join('\n');
const url='https://kflenmeizngmafwnwhgv.supabase.co/functions/v1/payment-reconcile';
const key='local-fixture-key-'.padEnd(40,'x');
function run(RECONCILE_URL='',RECONCILE_KEY='',exitCode=0) {
  return spawnSync('bash',['-e','-c',`curl() { printf 'MOCK_CURL\\n'; printf '%s\\n' "$@"; return ${exitCode}; }\n${script}`],{
    encoding:'utf8',env:{PATH:process.env.PATH,RECONCILE_URL,RECONCILE_KEY}});
}
test('scheduler stays inactive with no secrets, and flags incomplete configuration',()=>{
  const skipped=run();assert.equal(skipped.status,0);assert.match(skipped.stdout,/nothing to do/);
  assert.doesNotMatch(skipped.stdout,/MOCK_CURL/);
  for(const args of [[url,''],['',key]]) {
    const result=run(...args);assert.equal(result.status,1);assert.doesNotMatch(result.stdout,/MOCK_CURL/);
  }
});
test('scheduler rejects URL suffix tricks, userinfo and every other project before sending the key',()=>{
  for(const invalid of [
    'https://attacker.invalid/#.supabase.co/functions/v1/payment-reconcile',
    'https://attacker.invalid/?next=.supabase.co/functions/v1/payment-reconcile',
    'https://other.supabase.co/functions/v1/payment-reconcile',
    url.replace('https://','https://attacker@'),url+'?redirect=attacker',url+'#suffix',
    url.replace('https:','http:'),url.replace('.supabase.co', '.supabase.co.attacker.invalid'),
  ]) {
    const result=run(invalid,key);assert.equal(result.status,1,invalid);
    assert.doesNotMatch(result.stdout,/MOCK_CURL/);assert.ok(!result.stdout.includes(key));
  }
});
test('scheduler rejects short keys and header line breaks without leaking their value',()=>{
  for(const invalid of ['short',key+'\rInjected: value',key+'\nInjected: value']) {
    const result=run(url,invalid);assert.equal(result.status,1);
    assert.doesNotMatch(result.stdout,/MOCK_CURL/);assert.ok(!result.stdout.includes(invalid));
  }
});
test('configured scheduler uses an empty POST, bounded HTTPS and no redirect or automatic retry',()=>{
  const result=run(url,key);assert.equal(result.status,0);assert.match(result.stdout,/MOCK_CURL/);
  const args=result.stdout.trim().split('\n').slice(1);
  for(const option of ['--fail-with-body','--no-location','--connect-timeout','--max-time'])assert.ok(args.includes(option));
  assert.equal(args[args.indexOf('--proto')+1],'=https');assert.equal(args[args.indexOf('-X')+1],'POST');
  assert.equal(args[args.indexOf('-H')+1],`x-reconcile-key: ${key}`);assert.equal(args.at(-1),url);
  for(const option of ['--location','--retry','--data','-d'])assert.ok(!args.includes(option));
});
test('scheduler propagates delivery failure instead of reporting a successful run',()=>{
  assert.equal(run(url,key,22).status,22);
});
