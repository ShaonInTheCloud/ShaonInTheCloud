import {useState} from 'react';
import {View} from 'react-native';
import {router} from 'expo-router';
import {Body, Button, Card, Display, Field, Notice, Screen, Title} from '@/components/ui';
import {useStore} from '@/lib/store';
import {supabase} from '@/lib/supabase';
import {fill} from '@/lib/i18n';

export default function SignUp() {
  const {t, lang, edition} = useStore();
  const [email, setEmail] = useState(''), [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [sent, setSent] = useState(false);
  async function submit() {
    if (password.length < 10) { setError(t.passwordHint); return; }
    setBusy(true); setError('');
    // The same profile fields the website saves; confirmation goes through the website's redirect allowlist.
    const {data, error: failure} = await supabase.auth.signUp({email: email.trim(), password,
      options: {emailRedirectTo: 'https://mysafenestbd.com/account', data: {preferred_language: lang, edition}}});
    setBusy(false);
    if (failure) { setError(/rate|many/i.test(failure.message) ? t.errorRate : /network|fetch/i.test(failure.message) ? t.errorNetwork : failure.message); return; }
    if (data.session) { router.replace('/(tabs)'); return; }
    setSent(true);
  }
  if (sent) return (
    <Screen>
      <Card tone="alt">
        <Title>{t.checkInbox}</Title>
        <Body>{fill(t.confirmSent, {email: email.trim()})}</Body>
      </Card>
      <Button label={t.signIn} onPress={() => router.replace('/(auth)/sign-in')} />
    </Screen>
  );
  return (
    <Screen>
      <Button kind="text" label="‹ SafeNest" onPress={() => router.back()} style={{alignSelf: 'flex-start', paddingHorizontal: 0}} />
      <Display>{t.signUp}</Display>
      <View style={{gap: 14}}>
        <Field label={t.email} value={email} onChangeText={setEmail} autoCapitalize="none" autoComplete="email" keyboardType="email-address" textContentType="emailAddress" />
        <Field label={t.password} hint={t.passwordHint} value={password} onChangeText={setPassword} secureTextEntry autoComplete="new-password" textContentType="newPassword" onSubmitEditing={submit} />
      </View>
      <Notice text={error} tone="bad" />
      <Button label={t.signUp} onPress={submit} busy={busy} disabled={!email || !password} />
      <View style={{flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, flexWrap: 'wrap'}}>
        <Body muted>{t.haveAccount}</Body>
        <Button kind="text" label={t.signIn} onPress={() => router.replace('/(auth)/sign-in')} />
      </View>
    </Screen>
  );
}
