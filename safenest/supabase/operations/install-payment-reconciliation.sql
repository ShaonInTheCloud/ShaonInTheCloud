-- Run ONLY in an approved environment after applying payment_recovery migration.
-- Installation creates a dormant cron job; configuration remains disabled.
-- No provider credentials, payment mode change, checkout activation or grants.
begin;
create extension if not exists pg_cron;
create extension if not exists pg_net with schema extensions;
do $$ begin
  if to_regprocedure('payments.enqueue_reconciliation()') is null then raise exception 'payment_recovery_migration_required'; end if;
  if not exists(select 1 from payments.reconciliation_configuration where not enabled) then
    raise exception 'disable_reconciliation_before_installation';
  end if;
end $$;
select cron.schedule('safenest-payment-reconciliation','*/5 * * * *',
  'select payments.enqueue_reconciliation();');
commit;

-- Activation is separate and must preserve SSLCOMMERZ_CHECKOUT_ENABLED=false.
-- Provision a RANDOM server-only key (>=32 characters) as SSLCOMMERZ_RECONCILE_KEY
-- in Edge secrets AND Vault name safenest_payment_reconcile_key via secure tooling.
-- Never put literal secrets in cron commands, SQL files, source or public output.
-- Set the fixed own-project HTTPS endpoint/environment in private configuration,
-- then enabled=true only after provider validation/scheduler sandbox acceptance.
-- Rollback: set enabled=false, then cron.unschedule('safenest-payment-reconciliation').
