import {useCallback, useRef, useState} from 'react';
import {View} from 'react-native';
import * as WebBrowser from 'expo-web-browser';
import {useFocusEffect} from 'expo-router';
import {Body, Button, Card, Display, Label, Notice, Pill, Screen, Title} from '@/components/ui';
import {useStore} from '@/lib/store';
import {api, ApiError, type Catalogue, type Product} from '@/lib/api';
import {fill} from '@/lib/i18n';

const symbols: Record<string, string> = {BDT: '৳', USD: '$', EUR: '€'};
const price = (p: Product) => `${symbols[p.currency] ?? p.currency + ' '}${(p.amount_minor / 100).toFixed(2).replace(/\.00$/, '')}`;
const providerLabel = (t: ReturnType<typeof useStore>['t'], name: string) =>
  name === 'stripe' ? t.payCard : name === 'nowpayments' ? t.payCrypto : t.payLocal;
const checkoutOrigins: Record<string, string[]> = {
  stripe: ['https://checkout.stripe.com'],
  nowpayments: ['https://sandbox.nowpayments.io', 'https://nowpayments.io'],
  sslcommerz: ['https://sandbox.sslcommerz.com', 'https://securepay.sslcommerz.com'],
};
// Proposed prices shown while checkout is closed, mirroring pricing.html and global/pricing.html.
const proposed: Record<'bd' | 'global', Product[]> = {
  bd: [{code: 'monthly_v2', plan: 'monthly', region: 'bd', amount_minor: 37900, currency: 'BDT', duration_seconds: 2592000, providers: []},
    {code: 'quarterly_v1', plan: 'quarterly', region: 'bd', amount_minor: 99900, currency: 'BDT', duration_seconds: 7776000, providers: []},
    {code: 'annual_v2', plan: 'annual', region: 'bd', amount_minor: 379900, currency: 'BDT', duration_seconds: 31536000, providers: []}],
  global: [{code: 'global_monthly_v1', plan: 'monthly', region: 'global', amount_minor: 499, currency: 'USD', duration_seconds: 2592000, providers: []},
    {code: 'global_quarterly_v1', plan: 'quarterly', region: 'global', amount_minor: 1299, currency: 'USD', duration_seconds: 7776000, providers: []},
    {code: 'global_annual_v1', plan: 'annual', region: 'global', amount_minor: 3999, currency: 'USD', duration_seconds: 31536000, providers: []}],
};
function read(key: string) { try { return localStorage.getItem(key); } catch { return null; } }
function write(key: string, value: string | null) { try { value === null ? localStorage.removeItem(key) : localStorage.setItem(key, value); } catch {} }

type CatalogueState = {status: 'loading'} | {status: 'ready'; catalogue: Catalogue}
  | {status: 'error'; reason: 'network' | 'server'};

export default function Plans() {
  const {t, edition, session, testMode, testPlan, setTestPlan, lang} = useStore();
  const testGuest = testMode && !session;
  const [catalogueState, setCatalogueState] = useState<CatalogueState>({status: 'loading'});
  const loadId = useRef(0);
  const [busy, setBusy] = useState(''), [note, setNote] = useState(''), [tone, setTone] = useState<'good' | 'bad' | 'neutral'>('neutral');
  const pendingKey = `safenest.payment.${session?.user.id}`;
  const load = useCallback(async () => {
    const id = ++loadId.current;
    // Clear stale prices/buttons while rechecking availability. A failed fetch is never a closed checkout.
    setCatalogueState({status: 'loading'});
    try {
      const catalogue = await api.catalogue();
      if (!catalogue || typeof catalogue.available !== 'boolean' || !Array.isArray(catalogue.products) || !Array.isArray(catalogue.providers)) {
        throw new ApiError('invalid_catalogue', 502);
      }
      if (id === loadId.current) setCatalogueState({status: 'ready', catalogue});
    } catch (error) {
      if (id === loadId.current) setCatalogueState({status: 'error',
        reason: error instanceof ApiError && error.code === 'network_unavailable' ? 'network' : 'server'});
    }
    // Recover an existing order even when the catalogue cannot be loaded.
    const pending = read(pendingKey);
    if (pending) {
      try {
        const saved = JSON.parse(pending) as {order_id?: string};
        if (saved.order_id) {
          const status = await api.orderStatus(saved.order_id);
          if (status.state === 'paid') { setNote(t.paymentDone); setTone('good'); write(pendingKey, null); }
          else if (['cancelled', 'failed'].includes(status.state)) write(pendingKey, null);
          else { setNote(t.paymentPending); setTone('neutral'); }
        }
      } catch {}
    }
  }, [pendingKey, t]);
  useFocusEffect(useCallback(() => { void load(); return () => { loadId.current++; }; }, [load]));

  /** Lab build: the whole purchase flow with a simulated checkout. Nothing is charged or sent to a provider. */
  async function testPay(product: Product, provider: string) {
    setBusy(product.code + provider); setNote('');
    await new Promise(r => setTimeout(r, 900));
    setTestPlan({plan: product.plan, provider, ends_at: new Date(Date.now() + product.duration_seconds * 1000).toISOString()});
    setBusy(''); setNote(lang === 'bn' ? `টেস্ট পেমেন্ট সফল (${providerLabel(t, provider)}) — কোনো টাকা কাটা হয়নি।` : `Test payment successful (${providerLabel(t, provider)}). Nothing was charged.`); setTone('good');
  }

  async function pay(product: Product, provider: string) {
    setBusy(product.code + provider); setNote('');
    try {
      let saved = JSON.parse(read(pendingKey) ?? 'null') as {product?: string; provider?: string; idempotency_key?: string; order_id?: string} | null;
      if (saved && (saved.product !== product.code || saved.provider !== provider)) saved = null;
      saved ??= {product: product.code, provider, idempotency_key: `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 14)}`.padEnd(20, '0')};
      write(pendingKey, JSON.stringify(saved));
      const {order_id, checkout_url} = await api.order(product.code, provider, saved.idempotency_key!);
      const url = new URL(checkout_url);
      if (url.protocol !== 'https:' || !(checkoutOrigins[provider] ?? []).includes(url.origin)) throw new ApiError('invalid_provider_checkout');
      write(pendingKey, JSON.stringify({...saved, order_id}));
      await WebBrowser.openBrowserAsync(url.href, {dismissButtonStyle: 'close'});
      await load();
    } catch (error) {
      const code = error instanceof ApiError ? error.code : '';
      setNote(code === 'confirmed_account_required' ? t.confirmFirst : code === 'network_unavailable' ? t.errorNetwork : t.errorGeneric); setTone('bad');
    } finally { setBusy(''); }
  }

  const catalogue = catalogueState.status === 'ready' ? catalogueState.catalogue : null;
  const loading = catalogueState.status === 'loading';
  const failed = catalogueState.status === 'error';
  const live = catalogue?.available === true && !testGuest;
  // Open providers with no prices are an empty catalogue, not an operator-disabled checkout.
  const closed = catalogue?.available === false && catalogue.providers.length === 0;
  const products = testGuest || closed ? proposed[edition]
    : live ? catalogue!.products.filter(p => p.region === edition) : [];
  return (
    <Screen>
      <Display>{t.plansTitle}</Display>
      <Label>{edition === 'bd' ? `${t.editionBd} · ৳ BDT` : `${t.editionGlobal} · $ USD`}</Label>
      {testGuest ? <Notice text={testPlan && Date.parse(testPlan.ends_at) > Date.now()
          ? (lang === 'bn' ? `টেস্ট প্ল্যান চালু: ${t.perPlan[testPlan.plan] ?? testPlan.plan}` : `Test plan active: ${t.perPlan[testPlan.plan] ?? testPlan.plan}`)
          : (lang === 'bn' ? 'টেস্ট বিল্ড: যেকোনো পেমেন্ট চেষ্টা করুন — টাকা কাটা হবে না।' : 'Test build: try any payment method. Nothing is charged.')} tone="good" />
        : loading ? <Notice text={t.plansLoading} />
        : catalogueState.status === 'error' ? <Notice text={catalogueState.reason === 'network' ? t.plansNetworkError : t.plansServerError} tone="bad" />
        : closed ? <Notice text={t.plansClosed} />
        : products.length === 0 ? null
        : catalogue?.environment === 'sandbox' ? <Notice text={t.plansSandbox} tone="bad" /> : <Body muted size={14}>{t.paysOnProvider}</Body>}
      {!testGuest && catalogue && !closed && products.length === 0 ? <Notice text={t.plansEmpty} /> : null}
      <Notice text={note} tone={tone} />
      {products.map(p => (
        <Card key={p.code}>
          <View style={{flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline'}}>
            <Title>{t.perPlan[p.plan] ?? p.plan}</Title>
            <Display size={30}>{price(p)}</Display>
          </View>
          <Label>{fill(t.days, {n: Math.round(p.duration_seconds / 86400)})}</Label>
          {testGuest ? (edition === 'bd' ? ['sslcommerz', 'stripe', 'nowpayments'] : ['stripe', 'nowpayments']).map(name => (
            <Button key={name} label={providerLabel(t, name)} kind={name === 'stripe' ? 'primary' : name === 'sslcommerz' ? 'orange' : 'ghost'}
              busy={busy === p.code + name} disabled={!!busy} onPress={() => testPay(p, name)} />
          )) : live ? p.providers.map(name => (
            <Button key={name} label={providerLabel(t, name)} kind={name === 'stripe' ? 'primary' : 'ghost'}
              busy={busy === p.code + name} disabled={!!busy} onPress={() => pay(p, name)} />
          )) : <Pill text={t.plansClosed.split('.')[0]} tone="neutral" />}
        </Card>
      ))}
      <Button kind="text" label={!testGuest && failed ? t.plansRetry : t.refreshStatus}
        busy={loading} disabled={loading || !!busy} onPress={load} />
    </Screen>
  );
}
