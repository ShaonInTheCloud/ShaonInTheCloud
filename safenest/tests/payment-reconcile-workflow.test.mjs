import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const workflow=readFileSync(new URL('../../.github/workflows/safenest-payment-reconcile.yml',import.meta.url),'utf8');
const root=fileURLToPath(new URL('../../',import.meta.url));
const runner=workflow.match(/^        run: (.+)$/m)?.[1];
function run(extra={}) {
  return spawnSync('bash',['-e','-o','pipefail','-c',runner],{
    cwd:root,encoding:'utf8',env:{PATH:process.env.PATH,...extra}});
}

test('actual reconciliation workflow invokes the tested Node runner and stays inactive by default',()=>{
  assert.equal(runner,'node safenest/scripts/payment-reconcile.mjs');
  const result=run();assert.equal(result.status,0);assert.match(result.stdout,/disabled; nothing to do/);
  assert.match(workflow,/RECONCILE_ENABLED: \$\{\{ vars\.SAFENEST_RECONCILE_ENABLED \}\}/);
  assert.match(workflow,/group: safenest-payment-reconcile\n  cancel-in-progress: false/);
});

test('workflow credentials are scoped to invocation, excluded from checkout/setup, and are never persisted',()=>{
  const beforeCall=workflow.split('      - name: Call the reconcile function')[0];
  assert.doesNotMatch(beforeCall,/\$\{\{ secrets\.|RECONCILE_KEY|RECONCILE_URL/);
  assert.match(beforeCall,/persist-credentials: false/);
  assert.match(workflow,/permissions:\n  contents: read/);
});

test('actual workflow propagates invalid configuration failures without exposing credentials',()=>{
  const key='local-fixture-key-'.padEnd(40,'x');
  // Every case is rejected before fetch; these tests make no network requests.
  for(const configuration of [
    {RECONCILE_KEY:key},
    {RECONCILE_URL:'https://attacker.invalid/#.supabase.co/functions/v1/payment-reconcile',RECONCILE_KEY:key},
  ]) {
    const result=run({RECONCILE_ENABLED:'true',...configuration});
    assert.equal(result.status,1);assert.ok(!result.stdout.includes(key));assert.ok(!result.stderr.includes(key));
    assert.doesNotMatch(result.stdout+result.stderr,/attacker\.invalid/);
  }
});
