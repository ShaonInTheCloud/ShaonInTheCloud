import {useCallback, useEffect, useState} from 'react';
import {Image, Linking, Platform, Pressable, ScrollView, Text, View} from 'react-native';
import {router, useFocusEffect, type Href} from 'expo-router';
import {Body, Button, Card, Display, Label, Notice, Pill, Screen, Title} from '@/components/ui';
import {CountUp, NestOrb} from '@/components/motion';
import {useStore} from '@/lib/store';
import {api, ApiError, IOS_PROFILE_URL, type AccessResult} from '@/lib/api';
import {fill} from '@/lib/i18n';
import {fonts} from '@/lib/theme';

function when(iso: string, lang: 'en' | 'bn') {
  const d = new Date(iso);
  return d.toLocaleString(lang === 'bn' ? 'bn-BD' : 'en-GB', {day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit'});
}
function remaining(iso: string, lang: 'en' | 'bn') {
  const ms = Math.max(0, new Date(iso).getTime() - Date.now());
  const d = Math.floor(ms / 86400000), h = Math.floor((ms % 86400000) / 3600000);
  return lang === 'bn' ? `${d} দিন ${h} ঘণ্টা` : `${d}d ${h}h`;
}

/** One poster in the tools rail. */
function Poster({title, note, color, ink = '#FFFFFF', onPress}: {title: string; note: string; color: string; ink?: string; onPress: () => void}) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={title} onPress={onPress}
      style={({pressed}) => ({width: 164, height: 196, borderRadius: 24, padding: 16, justifyContent: 'flex-end', backgroundColor: color, transform: [{scale: pressed ? 0.96 : 1}]})}>
      <Text style={{fontFamily: fonts.display, fontSize: 24, lineHeight: 26, color: ink, letterSpacing: -0.8}}>{title}</Text>
      <Text style={{fontFamily: fonts.bodySemi, fontSize: 13, color: ink, opacity: 0.85, marginTop: 6}}>{note}</Text>
    </Pressable>
  );
}

export default function Home() {
  const {t, lang, theme, session, edition, email, testPlan} = useStore();
  const testGuest = !session;
  const [access, setAccess] = useState<AccessResult | null>(null);
  const [state, setState] = useState<'checking' | 'ok' | 'offline'>('checking');
  const [busy, setBusy] = useState(false), [note, setNote] = useState(''), [noteTone, setNoteTone] = useState<'good' | 'bad' | 'neutral'>('neutral');
  const load = useCallback(async () => {
    setState(access ? 'ok' : 'checking');
    if (testGuest) {
      const now = new Date();
      const live = testPlan && Date.parse(testPlan.ends_at) > +now ? testPlan : null;
      setAccess({active: true, user_id: 'test', server_now: now.toISOString(),
        entitlement: {id: 'test', plan_code: live ? live.plan : 'test', starts_at: now.toISOString(), ends_at: live ? live.ends_at : new Date(now.getTime() + 30 * 86400_000).toISOString()}});
      setState('ok'); return;
    }
    try { setAccess(await api.access()); setState('ok'); } catch { setState('offline'); }
  }, [access, testGuest, testPlan]);
  useFocusEffect(useCallback(() => { load(); }, [testPlan])); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { const id = setInterval(load, 60_000); return () => clearInterval(id); }, [load]);

  async function startTrial() {
    setBusy(true); setNote('');
    try {
      const result = await api.startTrial('monthly');
      if (result.entitlement) { setNote(fill(t.trialStarted, {when: when(result.entitlement.ends_at, lang)})); setNoteTone('good'); await load(); }
      else { setNote(t.trialUsed); setNoteTone('neutral'); }
    } catch (error) {
      const code = error instanceof ApiError ? error.code : '';
      setNote(code === 'confirmed_account_required' ? t.confirmFirst : code === 'network_unavailable' ? t.errorNetwork : t.errorGeneric);
      setNoteTone('bad');
    } finally { setBusy(false); }
  }

  const ent = access?.entitlement ?? null;
  const active = !!access?.active && !!ent;
  const trial = ent?.plan_code === 'trial';
  const status = state === 'checking' ? t.statusChecking : state === 'offline' ? t.statusOffline
    : active ? (trial ? t.statusTrial : t.statusActive) : t.statusNone;
  const go = (href: Href) => () => router.navigate(href);

  return (
    <Screen>
      <View style={{flexDirection: 'row', alignItems: 'center', gap: 12}}>
        <Image source={require('../../../assets/icon.png')} style={{width: 40, height: 40, borderRadius: 12}} />
        <View style={{flex: 1}}>
          <Text style={{fontFamily: fonts.display, fontSize: 22, color: theme.ink, letterSpacing: -0.5}}>{t.appName}</Text>
          <Label>{edition === 'bd' ? `${t.editionBd} ৳` : `${t.editionGlobal} $`}{email ? `  ${email}` : ''}</Label>
        </View>
      </View>

      <View style={{alignItems: 'center', marginTop: 4}}>
        <NestOrb size={250} active={active} />
        <View style={{marginTop: -6}}><Pill text={status} tone={active ? 'good' : state === 'offline' ? 'bad' : 'neutral'} /></View>
        {active && ent ? (
          <>
            <CountUp value={Math.floor(Math.max(0, (new Date(ent.ends_at).getTime() - Date.now()) / 86400000))}
              format={n => lang === 'bn' ? `${Math.floor(n)} দিন` : `${Math.floor(n)} days`}
              style={{marginTop: 14, textAlign: 'center', fontFamily: lang === 'bn' ? fonts.banglaDisplay : fonts.display, fontSize: 60, lineHeight: 64, letterSpacing: -2, color: theme.ink}} />
            <Body muted size={14} style={{textAlign: 'center'}}>{remaining(ent.ends_at, lang)} {lang === 'bn' ? 'বাকি' : 'left'}</Body>
            <Body muted size={14} style={{textAlign: 'center'}}>
              {ent.plan_code === 'test' ? t.testAccess : fill(t.endsIn, {when: when(ent.ends_at, lang)})}
            </Body>
          </>
        ) : (
          <>
            <Display size={36} style={{marginTop: 14, textAlign: 'center'}}>{t.protectionTitle}</Display>
            <Body muted style={{textAlign: 'center', maxWidth: 320}}>{state === 'offline' ? t.errorNetwork : t.noAccess}</Body>
          </>
        )}
      </View>

      {!active && state === 'ok' ? <Button label={t.startTrial} onPress={startTrial} busy={busy} /> : null}
      {Platform.OS === 'ios' ? (
        <Card tone="orange">
          <Title style={{color: '#FFFFFF'}}>{lang === 'bn' ? 'এই iPhone-এ সুরক্ষা চালু করুন' : 'Turn on protection on this iPhone'}</Title>
          <Body size={15} style={{color: '#FFFFFF'}}>{lang === 'bn'
            ? '১. নিচের বোতাম চাপুন, Safari-তে "Allow" দিন।\n২. Settings খুলে "Profile Downloaded" → Install।\n৩. Settings → General → VPN & Device Management → DNS → SafeNest।'
            : '1. Tap the button, then Allow in Safari.\n2. Open Settings → Profile Downloaded → Install.\n3. Settings → General → VPN & Device Management → DNS → SafeNest.'}</Body>
          <Button kind="primary" label={lang === 'bn' ? 'সুরক্ষা চালু করুন' : 'Turn on protection'} style={{backgroundColor: '#0E0110'}}
            onPress={() => Linking.openURL(IOS_PROFILE_URL)} />
          <Body size={13} style={{color: '#FFFFFF', opacity: 0.9}}>{lang === 'bn'
            ? 'এটি ৫ লাখ ৪১ হাজার জুয়ার সাইট ব্লক করে। Settings থেকে সরানো যায়।'
            : 'Blocks 541,000 gambling sites. It can be removed in Settings.'}</Body>
        </Card>
      ) : null}
      <Notice text={note} tone={noteTone} />

      <Title style={{marginTop: 6}}>{lang === 'bn' ? 'আপনার টুল' : 'Your tools'}</Title>
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{gap: 14, paddingRight: 20}} style={{marginHorizontal: -20, paddingLeft: 20}}>
        <Poster title={t.check} note={lang === 'bn' ? 'কোনো সাইট ব্লক কি না দেখুন' : 'Is a site on the blocklist?'} color={theme.pink} onPress={go('/(tabs)/check')} />
        <Poster title={lang === 'bn' ? 'সাহায্য' : 'Get help'} note={lang === 'bn' ? 'জুয়া, ঋণ, সংকট' : 'Gambling, debt, crisis'} color={theme.orange} onPress={go('/(tabs)/help')} />
        <Poster title={t.plans} note={lang === 'bn' ? 'মেয়াদ ও দাম' : 'Plans and prices'} color={theme.neon} onPress={go('/(tabs)/plans')} />
        <Poster title={lang === 'bn' ? 'অ্যান্ড্রয়েড অ্যাপ' : 'Android blocker'} note={lang === 'bn' ? 'ফোনে ব্লক চালু করুন' : 'Turn on blocking'} color={theme.surfaceAlt} onPress={() => Linking.openURL('https://mysafenestbd.com/account')} />
      </ScrollView>

      <Card tone="alt">
        <Title>{t.engineTitle}</Title>
        <Body size={15} muted>{t.engineBody}</Body>
      </Card>
      {state !== 'checking' ? <Button kind="text" label={t.refreshStatus} onPress={load} /> : null}
      <Body muted size={13}>{t.honest}</Body>
    </Screen>
  );
}
