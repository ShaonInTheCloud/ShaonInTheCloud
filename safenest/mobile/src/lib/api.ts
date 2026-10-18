import {supabase, SUPABASE_URL, SUPABASE_KEY} from './supabase';

// Thin, typed wrappers over the Edge Functions the website already uses.
// Every call that acts for a user sends the live session JWT; the server re-checks it.

export type Entitlement = {id: string; plan_code: string; starts_at: string; ends_at: string};
export type AccessResult = {active: boolean; user_id: string; server_now: string; entitlement: Entitlement | null};
export type Product = {code: string; plan: 'weekly' | 'monthly' | 'quarterly' | 'annual'; region: 'bd' | 'global';
  amount_minor: number; currency: string; duration_seconds: number; providers: string[]};
export type Catalogue = {available: boolean; environment?: 'sandbox' | 'live'; products: Product[];
  providers: {provider: string; checkout_origins: string[]}[]};
export type TrialResult = {entitlement?: Entitlement & {plan_code: 'trial'}; selected_plan_code?: string;
  already_claimed?: boolean; error?: string};
export type DomainCheck = {blocked: boolean; domain?: string; reason?: string; blocklist_version?: number;
  confidence?: string; brand?: string; error?: string};

export class ApiError extends Error {
  constructor(public code: string, public status = 0) { super(code); }
}

async function token() {
  const {data} = await supabase.auth.getSession();
  if (!data.session) throw new ApiError('authentication_required', 401);
  return data.session.access_token;
}

async function call<T>(name: string, init: RequestInit & {auth?: boolean} = {}): Promise<T> {
  const headers: Record<string, string> = {apikey: SUPABASE_KEY, 'Content-Type': 'application/json'};
  if (init.auth !== false) headers.Authorization = `Bearer ${await token()}`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 15_000);
  try {
    const response = await fetch(`${SUPABASE_URL}/functions/v1/${name}`, {...init, headers, signal: controller.signal});
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new ApiError(body?.error ?? `http_${response.status}`, response.status);
    return body as T;
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError('network_unavailable');
  } finally { clearTimeout(timer); }
}

export const api = {
  /** Verified paid/trial access for the signed-in account. */
  access: () => call<AccessResult>('protection-access', {method: 'POST', body: '{}'}),
  /** One 72-hour trial per confirmed account; the server picks the window. */
  startTrial: (plan: 'monthly' | 'quarterly' | 'annual') =>
    call<TrialResult>('start-trial', {method: 'POST', body: JSON.stringify({plan_code: plan})}),
  /** Public prices and the payment methods whose checkout the operator opened. */
  catalogue: () => call<Catalogue>('payment-catalogue', {method: 'GET', auth: false}),
  /** Creates an order and returns the provider's hosted checkout URL. */
  order: (product_code: string, provider: string, idempotency_key: string) =>
    call<{order_id: string; checkout_url: string}>('payment-order', {method: 'POST',
      body: JSON.stringify({product_code, provider, idempotency_key, customer: {}})}),
  orderStatus: (order_id: string) =>
    call<{order_id: string; state: string; environment: string}>('payment-status', {method: 'POST', body: JSON.stringify({order_id})}),
  /**
   * Is a hostname a known gambling domain? Asks the signed catalogue first; when no signed release is published
   * yet, asks the live SafeNest DNS filter (the same 541k list the iPhone profile uses).
   */
  checkDomain: async (domain: string): Promise<DomainCheck> => {
    try { return await call<DomainCheck>(`intelligence/check?domain=${encodeURIComponent(domain.trim())}`, {method: 'GET', auth: false}); }
    catch (error) {
      if (error instanceof ApiError && error.code === 'network_unavailable') throw error;
      return {blocked: await dnsFilterBlocks(domain.trim()), domain: domain.trim(), confidence: 'SafeNest DNS filter'};
    }
  },
};

/** Safari opens this and offers to install the SafeNest DNS profile (blocks the same 541k gambling sites). */
export const IOS_PROFILE_URL = `${SUPABASE_URL}/functions/v1/dns-query?profile=ios`;

/** One A-record lookup through the SafeNest DNS-over-HTTPS filter; blocked names answer 0.0.0.0. */
export async function dnsFilterBlocks(host: string): Promise<boolean> {
  const labels = host.toLowerCase().split('.').filter(Boolean);
  const bytes: number[] = [0x5a, 0xfe, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0];
  for (const l of labels) { bytes.push(l.length); for (const ch of l) bytes.push(ch.charCodeAt(0) & 0x7f); }
  bytes.push(0, 0, 1, 0, 1);
  let response: Response;
  try {
    response = await fetch(`${SUPABASE_URL}/functions/v1/dns-query`, {method: 'POST', headers: {'Content-Type': 'application/dns-message', Accept: 'application/dns-message'}, body: new Uint8Array(bytes)});
  } catch { throw new ApiError('network_unavailable'); }
  if (!response.ok) throw new ApiError(`http_${response.status}`, response.status);
  const answer = new Uint8Array(await response.arrayBuffer());
  const count = (answer[6] << 8) | answer[7];
  if (!count || answer.length < 4) return false;
  return answer.slice(answer.length - 4).every(b => b === 0);
}
