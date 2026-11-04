-- Defence in depth (security audit 2026-11-01): payments.play_subscriptions has RLS on and no client grants,
-- but no policy said so explicitly. Match every other payments table: clients are refused outright.
create policy deny_clients on payments.play_subscriptions as restrictive for all
  to anon, authenticated using (false) with check (false);
