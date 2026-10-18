import {Linking, View} from 'react-native';
import Constants from 'expo-constants';
import {router} from 'expo-router';
import {Body, Button, Card, Choice, Display, Label, Screen, Title} from '@/components/ui';
import {useStore} from '@/lib/store';
import {supabase} from '@/lib/supabase';

export default function Settings() {
  const {t, lang, setLang, edition, setEdition, session, email, setGuest, testMode, testPlan, setTestPlan} = useStore();
  async function signOut() { setGuest(false); if (session) await supabase.auth.signOut(); router.replace('/'); }
  return (
    <Screen>
      <Display>{t.settings}</Display>
      <Card>
        <Title>{t.language}</Title>
        <Choice selected={lang === 'en'} title="English" onPress={() => setLang('en')} />
        <Choice selected={lang === 'bn'} title="বাংলা" onPress={() => setLang('bn')} />
      </Card>
      <Card>
        <Title>{t.edition}</Title>
        <Choice selected={edition === 'bd'} title={`${t.editionBd} · ৳`} note={t.editionBdNote} onPress={() => setEdition('bd')} />
        <Choice selected={edition === 'global'} title={`${t.editionGlobal} · $`} note={t.editionGlobalNote} onPress={() => setEdition('global')} />
      </Card>
      <Card>
        <Title>{t.account}</Title>
        <View><Label>{t.signedInAs}</Label><Body>{email}</Body></View>
        <Button label={session ? t.signOut : t.testReset} kind="ghost" onPress={signOut} />
        {testMode && testPlan ? <Button kind="text" label={lang === 'bn' ? 'টেস্ট প্ল্যান মুছুন' : 'Clear test plan'} onPress={() => setTestPlan(null)} /> : null}
        <Button label={t.deleteAccount} kind="text" onPress={() => Linking.openURL('https://mysafenestbd.com/delete-account.html')} />
      </Card>
      <View style={{flexDirection: 'row', flexWrap: 'wrap', gap: 4}}>
        <Button kind="text" label={t.privacy} onPress={() => Linking.openURL('https://mysafenestbd.com/privacy.html')} />
        <Button kind="text" label={t.support} onPress={() => Linking.openURL('https://mysafenestbd.com/support.html')} />
      </View>
      <Label>{t.version} {Constants.expoConfig?.version ?? '0.1.0'}</Label>
    </Screen>
  );
}
