// Presentation only: paid access is verified by protection-access and database RLS.
// An entitlement never proves that a VPN is running on a customer's phone.
export function entitlementState(data, userId) {
  if (!data || data.user_id !== userId || typeof data.active !== 'boolean') return { kind: 'unavailable' };
  const now = Date.parse(data.server_now);
  if (!Number.isFinite(now)) return { kind: 'unavailable' };
  if (!data.active) return data.entitlement === null ? { kind: 'inactive' } : { kind: 'unavailable' };
  const period = data.entitlement;
  const starts = Date.parse(period?.starts_at), ends = Date.parse(period?.ends_at);
  if (!period?.id || !['weekly', 'monthly', 'quarterly', 'annual', 'trial'].includes(period.plan_code) ||
      !Number.isFinite(starts) || !Number.isFinite(ends) || starts > now || ends <= now) {
    return { kind: 'unavailable' };
  }
  return { kind: 'active', plan: period.plan_code, endsAt: new Date(ends).toISOString() };
}
