import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const config = readFileSync(new URL('../supabase/config.toml', import.meta.url), 'utf8');
const setting = name => config.match(new RegExp(`\\[functions\\.${name}\\]\\s*\\nverify_jwt\\s*=\\s*(true|false)`))?.[1];

test('gateway and scheduler callbacks are reachable without a user JWT', () => {
  for (const name of ['payment-notification', 'payment-return', 'payment-reconcile', 'payment-catalogue'])
    assert.equal(setting(name), 'false', `${name} must set verify_jwt = false`);
});

test('functions that act for a signed-in user keep JWT verification', () => {
  for (const name of ['delete-account', 'protection-access', 'start-trial', 'payment-order', 'payment-status'])
    assert.equal(setting(name), 'true', `${name} must keep verify_jwt = true`);
});

// Independent PR additions can merge without conflict yet produce invalid TOML.
test('function configuration has no duplicate table declarations after consolidation',()=>{
  const tables=[...config.matchAll(/^\[([^\]]+)\]$/gm)].map(match=>match[1]);
  assert.equal(new Set(tables).size,tables.length,'TOML table declarations must be unique');
});
