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
  /** Asks the published blocklist whether a hostname is a known gambling domain. */
  checkDomain: (domain: string) =>
    call<DomainCheck>(`intelligence/check?domain=${encodeURIComponent(domain.trim())}`, {method: 'GET', auth: false}),
};
