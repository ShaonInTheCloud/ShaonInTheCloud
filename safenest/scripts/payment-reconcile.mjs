import {pathToFileURL} from 'node:url';

// Pin the intended project, not a shell glob: a glob can match an attacker host
// followed by /.supabase.co/... and disclose the reconcile credential to it.
export const reconcileUrl='https://kflenmeizngmafwnwhgv.supabase.co/functions/v1/payment-reconcile';

export async function runReconciliation(env=process.env,fetcher=fetch,log=console.log) {
  if(env.RECONCILE_ENABLED!=='true') {
    log('Reconciliation is disabled; nothing to do.');return;
  }
  const url=env.RECONCILE_URL,key=env.RECONCILE_KEY;
  if(!url && !key) {log('Reconciliation secrets are not configured; nothing to do.');return;}
  if(!url || !key) throw new Error('Reconciliation configuration is incomplete; configure both URL and key');
  if(url!==reconcileUrl) throw new Error('Reconciliation URL must exactly match the SafeNest project endpoint');
  if(key.length<32 || key.length>200 || !/^[\x21-\x7e]+$/.test(key))
    throw new Error('Reconciliation key must be 32–200 printable ASCII characters without whitespace');
  let response;
  try {
    response=await fetcher(url,{method:'POST',headers:{'x-reconcile-key':key},
      redirect:'error',signal:AbortSignal.timeout(60_000)});
  } catch {throw new Error('Reconciliation request failed; check function health and connectivity');}
  // Never log the configured URL/key or an arbitrary upstream response body.
  if(!response.ok) throw new Error(`Reconciliation returned HTTP ${response.status}`);
  let result;try {result=await response.json();}catch {throw new Error('Invalid reconciliation response');}
  // Accept the currently deployed handler until the reviewed function fix is
  // explicitly deployed. Old responses still fail visibly when review > 0.
  const {processed,review,failed=0}=result??{};
  if(![processed,review,failed].every(n=>Number.isSafeInteger(n)&&n>=0) || processed+review+failed>3)
    throw new Error('Invalid reconciliation counts');
  log(`Reconciliation: processed=${processed}, review=${review}, failed=${failed}`);
  if(failed>0 || (result.failed===undefined && review>0))
    throw new Error('Reconciliation has unresolved failures; inspect the function and ledger');
  if(review>0) log('::warning::Reconciliation has orders requiring provider or operator review');
}

if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  try {await runReconciliation();}catch(error) {console.error(error.message);process.exitCode=1;}
}
