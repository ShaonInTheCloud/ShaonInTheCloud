import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync, mkdtempSync, writeFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';

const functions=readFileSync(new URL('../../.github/workflows/safenest-deploy-backend.yml',import.meta.url),'utf8');
const migrations=readFileSync(new URL('../../.github/workflows/safenest-migrate-backend.yml',import.meta.url),'utf8');
// Extract the literal shell body of an audited step for execution against a mock CLI.
function script(workflow,name) {
  const step=workflow.split(`      - name: ${name}\n`)[1]?.split(/\n      - /)[0];
  assert.ok(step,`missing step: ${name}`);
  const literal=step.split('        run: |\n')[1];
  if(literal)return literal.split('\n').map(line=>line.replace(/^          /,'')).join('\n');
  return step.match(/        run: (.+)/)?.[1];
}
function fixture(fn) {
  const dir=mkdtempSync(join(tmpdir(),'safenest-deploy-'));
  try {
    writeFileSync(join(dir,'npx'),'#!/bin/sh\nprintf "%s\\n" "$*" >> "$MOCK_LOG"\nexit "${MOCK_EXIT:-0}"\n',{mode:0o755});
    const env={...process.env,PATH:dir+':'+process.env.PATH,MOCK_LOG:join(dir,'calls'),
      SUPABASE_CLI:'supabase@2.120.0',PROJECT_REF:'fixture',GITHUB_SHA:'a'.repeat(40)};
    fn((body,extra={})=>spawnSync('bash',['-e','-o','pipefail','-c',body],{env:{...env,...extra},encoding:'utf8'}),
      ()=>readFileSync(env.MOCK_LOG,'utf8').trim().split('\n'));
  }finally{rmSync(dir,{recursive:true,force:true});}
}

test('function workflow cannot access SQL migrations or database credentials',()=>{
  assert.doesNotMatch(functions,/SUPABASE_DB_PASSWORD|\blink --project-ref|\bdb push|\bmigration (?:list|repair)|--no-verify-jwt|--prune/);
  assert.match(functions,/default: 'preview'/);
  assert.match(functions,/needs: preview/);
  assert.match(functions,/github.ref == 'refs\/heads\/main' && inputs.deploy == 'deploy-payment-functions'/);
});

test('both manual operations retain production environment, least permissions and shared serialization',()=>{
  for(const workflow of [functions,migrations]) {
    assert.match(workflow,/on:\n  workflow_dispatch:/);
    assert.doesNotMatch(workflow,/\n  (?:push|pull_request|schedule):/);
    assert.match(workflow,/permissions:\n  contents: read/);
    assert.match(workflow,/environment: production/);
    assert.match(workflow,/group: safenest-deploy-backend\n  cancel-in-progress: false/);
    assert.doesNotMatch(workflow,/continue-on-error:|if:.*always\(\)/);
  }
  const preview=functions.split('  preview:\n')[1].split('  deploy:\n')[0];
  assert.doesNotMatch(preview,/secrets\.|environment:|npx/);
});

test('deployment invokes exactly six functions with explicit project and no JWT override, stopping on failure',()=>fixture((run,calls)=>{
  const body=script(functions,'Deploy payment functions (per-function verify_jwt comes from config.toml)');
  assert.equal(run(body).status,0);
  assert.deepEqual(calls(),['payment-notification','payment-return','payment-reconcile','payment-catalogue','payment-order','payment-status']
    .map(name=>`--yes supabase@2.120.0 functions deploy ${name} --project-ref fixture --use-api`));
  assert.equal(run(body,{MOCK_EXIT:'1'}).status,1);
  assert.equal(calls().length,7);
}));

test('migration application is separately opted in and cannot deploy functions or repair history',()=>{
  assert.match(migrations,/default: 'dry-run'/);
  assert.match(migrations,/if: \$\{\{ github.ref == 'refs\/heads\/main' \}\}/);
  assert.doesNotMatch(migrations,/functions deploy|migration repair|--include-all|--include-seed|--include-roles/);
  const step=migrations.split('      - name: Apply separately authorized migrations\n')[1];
  assert.match(step,/if: \$\{\{ inputs.apply == 'apply-migrations' \}\}/);
  assert.ok(migrations.indexOf('db push --linked --dry-run')<migrations.indexOf('db push --linked --yes'));
  fixture((run,calls)=>{
    assert.equal(run(script(migrations,'Pending migrations (dry run; stop on history mismatch)')).status,0);
    assert.deepEqual(calls(),['--yes supabase@2.120.0 db push --linked --dry-run --skip-vault']);
  });
});

test('migration apply rejects absent or stale reviewed SHA, including shell metacharacters',()=>fixture((run)=>{
  const body=script(migrations,'Verify reviewed commit before any database connection');
  for(const reviewed of ['', 'b'.repeat(40), '$(exit 0)', '`exit 0`'])
    assert.equal(run(body,{REVIEWED_SHA:reviewed}).status,1);
  assert.equal(run(body,{REVIEWED_SHA:'a'.repeat(40)}).status,0);
  assert.ok(migrations.indexOf('Verify reviewed commit')<migrations.indexOf('      - name: Link project'));
}));
