import {useState} from 'react';
import {View} from 'react-native';
import {router} from 'expo-router';
import {Body, Button, Display, Field, Notice, Screen} from '@/components/ui';
import {useStore} from '@/lib/store';
import {supabase} from '@/lib/supabase';

export default function SignIn() {
  const {t} = useStore();
  const [email, setEmail] = useState(''), [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [info, setInfo] = useState('');
  async function submit() {
    setBusy(true); setError(''); setInfo('');
    const {error: failure} = await supabase.auth.signInWithPassword({email: email.trim(), password});
    setBusy(false);
    if (failure) {
      setError(/invalid/i.test(failure.message) ? t.errorAuth : /rate|many/i.test(failure.message) ? t.errorRate
        : /confirm/i.test(failure.message) ? t.confirmFirst : /network|fetch/i.test(failure.message) ? t.errorNetwork : t.errorGeneric);
      return;
    }
    router.replace('/(tabs)');
  }
  async function reset() {
    if (!email.trim()) { setError(t.email); return; }
    setBusy(true); setError('');
    await supabase.auth.resetPasswordForEmail(email.trim(), {redirectTo: 'https://mysafenestbd.com/account?mode=recovery'});
    setBusy(false); setInfo(t.resetSent);
  }
  return (
    <Screen>
      <Button kind="text" label="‹ SafeNest" onPress={() => router.back()} style={{alignSelf: 'flex-start', paddingHorizontal: 0}} />
      <Display>{t.signIn}</Display>
      <View style={{gap: 14}}>
        <Field label={t.email} value={email} onChangeText={setEmail} autoCapitalize="none" autoComplete="email" keyboardType="email-address" textContentType="emailAddress" />
        <Field label={t.password} value={password} onChangeText={setPassword} secureTextEntry autoComplete="current-password" textContentType="password" onSubmitEditing={submit} />
      </View>
      <Notice text={error} tone="bad" />
      <Notice text={info} tone="good" />
      <Button label={t.signIn} onPress={submit} busy={busy} disabled={!email || !password} />
      <Button kind="text" label={t.forgot} onPress={reset} disabled={busy} />
      <View style={{flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, flexWrap: 'wrap'}}>
        <Body muted>{t.noAccount}</Body>
        <Button kind="text" label={t.signUp} onPress={() => router.replace('/(auth)/sign-up')} />
      </View>
    </Screen>
  );
}
