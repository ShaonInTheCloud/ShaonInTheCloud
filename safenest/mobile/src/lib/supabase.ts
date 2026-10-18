import 'expo-sqlite/localStorage/install';
import {AppState} from 'react-native';
import {createClient} from '@supabase/supabase-js';

// Same Supabase project as the website and the Android app. The publishable key is public
// by design (it is in the website bundle too); row-level security limits what it can read.
export const SUPABASE_URL = process.env.EXPO_PUBLIC_SUPABASE_URL ?? 'https://kflenmeizngmafwnwhgv.supabase.co';
export const SUPABASE_KEY = process.env.EXPO_PUBLIC_SUPABASE_PUBLISHABLE_KEY ?? 'sb_publishable_jt2VeNCAATz3iEiebx2Kog_ZiZVL7Vl';

export const supabase = createClient(SUPABASE_URL, SUPABASE_KEY, {
  auth: {
    storage: localStorage,
    autoRefreshToken: true,
    persistSession: true,
    detectSessionInUrl: false,
  },
});

// Refresh tokens only while the app is in the foreground.
AppState.addEventListener('change', state => {
  if (state === 'active') supabase.auth.startAutoRefresh();
  else supabase.auth.stopAutoRefresh();
});
