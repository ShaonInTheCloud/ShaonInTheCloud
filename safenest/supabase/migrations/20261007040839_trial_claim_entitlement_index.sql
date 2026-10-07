set local lock_timeout = '3s';
set local statement_timeout = '30s';
create index if not exists trial_claims_entitlement_idx on payments.trial_claims (entitlement_id);
