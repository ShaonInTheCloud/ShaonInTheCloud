import {useCallback, useEffect, useState} from 'react';
import {Image, Linking, View} from 'react-native';
import {useFocusEffect} from 'expo-router';
import {Body, Button, Card, Display, Label, Notice, Pill, Screen, Title} from '@/components/ui';
import {useStore} from '@/lib/store';
import {api, ApiError, type AccessResult} from '@/lib/api';
import {fill} from '@/lib/i18n';

function when(iso: string, lang: 'en' | 'bn') {
  const d = new Date(iso);
  return d.toLocaleString(lang === 'bn' ? 'bn-BD' : 'en-GB', {day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit'});
}

export default function Home() {
  const {t, lang, theme, session, edition, email} = useStore();
  const testGuest = !session; // only reachable without a session in the test build
  const [access, setAccess] = useState<AccessResult | null>(null);
  const [state, setState] = useState<'checking' | 'ok' | 'offline'>('checking');
  const [busy, setBusy] = useState(false), [note, setNote] = useState(''), [noteTone, setNoteTone] = useState<'good' | 'bad' | 'neutral'>('neutral');
  const load = useCallback(async () => {
    setState(access ? 'ok' : 'checking');
    if (testGuest) {
      const now = new Date();
      setAccess({active: true, user_id: 'test', server_now: now.toISOString(),
        entitlement: {id: 'test', plan_code: 'test', starts_at: now.toISOString(), ends_at: new Date(now.getTime() + 30 * 86400_000).toISOString()}});
      setState('ok'); return;
    }
    try { setAccess(await api.access()); setState('ok'); } catch { setState('offline'); }
  }, [access, testGuest]);
  useFocusEffect(useCallback(() => { load(); }, [])); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { const id = setInterval(load, 60_000); return () => clearInterval(id); }, [load]);

  async function startTrial() {
    setBusy(true); setNote('');
    try {
      const result = await api.startTrial('monthly');
      if (result.entitlement) {
        setNote(fill(t.trialStarted, {when: when(result.entitlement.ends_at, lang)})); setNoteTone('good');
        await load();
      } else { setNote(t.trialUsed); setNoteTone('neutral'); }
    } catch (error) {
      const code = error instanceof ApiError ? error.code : '';
      setNote(code === 'confirmed_account_required' ? t.confirmFirst : code === 'network_unavailable' ? t.errorNetwork : t.errorGeneric);
      setNoteTone('bad');
    } finally { setBusy(false); }
  }

  const ent = access?.entitlement ?? null;
  const active = !!access?.active && !!ent;
  const trial = ent?.plan_code === 'trial';
  const pill = state === 'checking' ? {text: t.statusChecking, tone: 'neutral' as const}
    : state === 'offline' ? {text: t.statusOffline, tone: 'bad' as const}
    : active ? {text: trial ? t.statusTrial : t.statusActive, tone: 'good' as const}
    : {text: t.statusNone, tone: 'neutral' as const};

  return (
    <Screen>
      <View style={{flexDirection: 'row', alignItems: 'center', gap: 12}}>
        <Image source={require('../../../assets/icon.png')} style={{width: 44, height: 44, borderRadius: 13}} />
        <View style={{flex: 1}}>
          <Display size={28}>{t.appName}</Display>
          <Label>{edition === 'bd' ? `${t.editionBd} · ৳` : `${t.editionGlobal} · $`} · {email}</Label>
        </View>
      </View>

      <Card tone={active ? 'pink' : 'surface'}>
        <View style={{flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center'}}>
          <Title style={active ? {color: theme.onPink} : undefined}>{t.protectionTitle}</Title>
          {active ? <View style={{backgroundColor: '#ffffff33', borderRadius: 999, paddingHorizontal: 12, paddingVertical: 6}}>
            <Label style={{color: theme.onPink}}>{pill.text}</Label></View> : <Pill text={pill.text} tone={pill.tone} />}
        </View>
        {active && ent ? (
          <View style={{gap: 4}}>
            <Display size={30} style={{color: theme.onPink}}>{fill(t.endsIn, {when: when(ent.ends_at, lang)})}</Display>
            <Body size={14} style={{color: theme.onPink, opacity: .85}}>{ent.plan_code === 'test' ? t.testAccess : `${t.perPlan[ent.plan_code] ?? ent.plan_code} · ${t.startedOn}`}</Body>
          </View>
        ) : state === 'offline' ? <Body muted>{t.errorNetwork}</Body>
          : <Body muted>{t.noAccess}</Body>}
        {!active && state === 'ok' ? <Button label={t.startTrial} onPress={startTrial} busy={busy} /> : null}
        {state !== 'checking' ? <Button kind={active ? 'ghost' : 'text'} label={t.refreshStatus} onPress={load}
          style={active ? {borderColor: theme.onPink} : undefined} /> : null}
      </Card>
      <Notice text={note} tone={noteTone} />

      <Card tone="alt">
        <Title>{t.engineTitle}</Title>
        <Body size={15}>{t.engineBody}</Body>
        <Button kind="text" label={t.engineAndroid} onPress={() => Linking.openURL('https://mysafenestbd.com/account')} style={{alignSelf: 'flex-start', paddingHorizontal: 0}} />
      </Card>

      <Body muted size={14}>{t.honest}</Body>
    </Screen>
  );
}
