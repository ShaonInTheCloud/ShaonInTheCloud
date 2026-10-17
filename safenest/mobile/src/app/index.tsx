import {Redirect, router} from 'expo-router';
import {Image, View} from 'react-native';
import {Body, Button, Choice, Display, Label, Screen} from '@/components/ui';
import {useStore} from '@/lib/store';

export default function Welcome() {
  const {t, lang, setLang, edition, setEdition, signedIn, testMode, setGuest, theme} = useStore();
  if (signedIn) return <Redirect href="/(tabs)" />;
  return (
    <Screen>
      <View style={{flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between'}}>
        <Image source={require('../../assets/icon.png')} style={{width: 56, height: 56, borderRadius: 16}} accessibilityLabel="SafeNest" />
        <View style={{flexDirection: 'row', gap: 6}}>
          {(['en', 'bn'] as const).map(code => (
            <Button key={code} kind={lang === code ? 'primary' : 'ghost'} label={code === 'en' ? 'English' : 'বাংলা'} onPress={() => setLang(code)}
              style={{minHeight: 40, paddingHorizontal: 14}} />
          ))}
        </View>
      </View>
      <View style={{gap: 12, marginTop: 24}}>
        <Display size={40}>{t.welcomeTitle}</Display>
        <Body muted>{t.welcomeBody}</Body>
      </View>
      <View style={{gap: 10, marginTop: 12}}>
        <Label>{t.chooseEdition}</Label>
        <Choice selected={edition === 'bd'} title={`${t.editionBd} · ৳`} note={t.editionBdNote} onPress={() => setEdition('bd')} />
        <Choice selected={edition === 'global'} title={`${t.editionGlobal} · $`} note={t.editionGlobalNote} onPress={() => setEdition('global')} />
      </View>
      <View style={{gap: 10, marginTop: 16}}>
        {testMode ? <Button label={t.testEnter} onPress={() => { setGuest(true); router.replace('/(tabs)'); }} /> : null}
        <Button label={t.signIn} kind={testMode ? 'ghost' : 'primary'} onPress={() => router.push('/(auth)/sign-in')} />
        {testMode ? null : <Button label={t.signUp} kind="ghost" onPress={() => router.push('/(auth)/sign-up')} />}
      </View>
      <Body muted size={14} style={{marginTop: 8, color: theme.muted}}>{t.honest}</Body>
    </Screen>
  );
}
