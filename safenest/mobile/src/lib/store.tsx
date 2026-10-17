import React, {createContext, useContext, useEffect, useMemo, useState} from 'react';
import {useColorScheme} from 'react-native';
import type {Session} from '@supabase/supabase-js';
import {supabase} from './supabase';
import {strings, type Edition, type Lang, type Strings} from './i18n';
import {palette, type Theme} from './theme';

type Store = {
  lang: Lang; setLang: (l: Lang) => void;
  edition: Edition; setEdition: (e: Edition) => void;
  t: Strings; theme: Theme;
  session: Session | null; ready: boolean;
  /** Test build: no login, no paywall. Set EXPO_PUBLIC_TEST_MODE=1 when publishing. */
  testMode: boolean; guest: boolean; setGuest: (g: boolean) => void; signedIn: boolean; email: string;
  /** Lab build: a plan 'bought' with a simulated checkout. Never charged, stored on this phone only. */
  testPlan: TestPlan | null; setTestPlan: (p: TestPlan | null) => void;
};
export const TEST_MODE = process.env.EXPO_PUBLIC_TEST_MODE === '1';
export type TestPlan = {plan: string; provider: string; ends_at: string};
const Ctx = createContext<Store | null>(null);

// Settings live in expo-sqlite's localStorage shim, next to the Supabase session.
function read(key: string, fallback: string) {
  try { return localStorage.getItem(key) ?? fallback; } catch { return fallback; }
}
function write(key: string, value: string) { try { localStorage.setItem(key, value); } catch {} }

export function StoreProvider({children}: {children: React.ReactNode}) {
  const [lang, setLangState] = useState<Lang>(() => (read('safenest.lang', 'en') === 'bn' ? 'bn' : 'en'));
  const [edition, setEditionState] = useState<Edition>(() => (read('safenest.edition', 'bd') === 'global' ? 'global' : 'bd'));
  const [session, setSession] = useState<Session | null>(null);
  const [ready, setReady] = useState(false);
  // Lab build: straight in as a guest (no login); 'Back to the welcome screen' in Settings sets this to '0'.
  const [guest, setGuestState] = useState(() => TEST_MODE && read('safenest.guest', '1') !== '0');
  const [testPlan, setTestPlanState] = useState<TestPlan | null>(() => { try { return JSON.parse(read('safenest.testplan', 'null')); } catch { return null; } });
  const scheme = useColorScheme();
  useEffect(() => {
    supabase.auth.getSession().then(({data}) => { setSession(data.session); setReady(true); });
    const {data: sub} = supabase.auth.onAuthStateChange((_event, next) => setSession(next));
    return () => sub.subscription.unsubscribe();
  }, []);
  const value = useMemo<Store>(() => ({
    lang, setLang: l => { setLangState(l); write('safenest.lang', l); },
    edition, setEdition: e => { setEditionState(e); write('safenest.edition', e); },
    t: strings[lang], theme: palette(scheme === 'dark'),
    session, ready,
    testMode: TEST_MODE, guest, setGuest: g => { setGuestState(g); write('safenest.guest', g ? '1' : '0'); },
    testPlan, setTestPlan: p => { setTestPlanState(p); write('safenest.testplan', JSON.stringify(p)); },
    signedIn: !!session || (TEST_MODE && guest), email: session?.user.email ?? (TEST_MODE ? strings[lang].testGuest : ''),
  }), [lang, edition, scheme, session, ready, guest, testPlan]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useStore() {
  const store = useContext(Ctx);
  if (!store) throw new Error('useStore outside StoreProvider');
  return store;
}
