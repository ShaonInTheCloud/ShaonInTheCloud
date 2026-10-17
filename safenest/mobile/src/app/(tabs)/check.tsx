import {useState} from 'react';
import {View} from 'react-native';
import {Body, Button, Card, Display, Field, Label, Notice, Pill, Screen, Title} from '@/components/ui';
import {useStore} from '@/lib/store';
import {api, ApiError, type DomainCheck} from '@/lib/api';
import {fill} from '@/lib/i18n';

const hostPattern = /^(?=.{1,253}$)(?!-)[a-z0-9-]+(\.[a-z0-9-]+)+$/i;
export function normalizeHost(input: string) {
  let value = input.trim().toLowerCase();
  value = value.replace(/^[a-z]+:\/\//, '').split(/[/?#]/)[0].replace(/^www\./, '').replace(/:\d+$/, '');
  return hostPattern.test(value) ? value : null;
}

export default function Check() {
  const {t, theme} = useStore();
  const [input, setInput] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [result, setResult] = useState<DomainCheck | null>(null);
  async function run() {
    const host = normalizeHost(input);
    setError(''); setResult(null);
    if (!host) { setError(t.invalidHost); return; }
    setBusy(true);
    try { setResult(await api.checkDomain(host)); }
    catch (e) { setError(e instanceof ApiError && e.code === 'network_unavailable' ? t.errorNetwork : t.errorGeneric); }
    finally { setBusy(false); }
  }
  return (
    <Screen>
      <Display>{t.checkTitle}</Display>
      <Body muted>{t.checkHint}</Body>
      <Field label="URL" value={input} onChangeText={setInput} autoCapitalize="none" autoCorrect={false} keyboardType="url" placeholder="example.com" onSubmitEditing={run} returnKeyType="search" />
      <Button label={t.checkButton} onPress={run} busy={busy} disabled={!input.trim()} />
      <Notice text={error} tone="bad" />
      {result ? (
        <Card tone={result.blocked ? 'pink' : 'surface'}>
          <Pill text={result.blocked ? t.blocked : t.notBlocked} tone={result.blocked ? 'bad' : 'good'} />
          <Title style={result.blocked ? {color: theme.onPink} : undefined}>{normalizeHost(input)}</Title>
          {result.blocked ? (
            <View style={{gap: 2}}>
              {result.brand ? <Body style={{color: theme.onPink}}>{result.brand}</Body> : null}
              {result.confidence ? <Label style={{color: theme.onPink}}>{result.confidence}</Label> : null}
            </View>
          ) : <Body muted size={14}>{t.notBlockedNote}</Body>}
          {result.blocklist_version ? <Label style={result.blocked ? {color: theme.onPink, opacity: .8} : undefined}>{fill(t.listVersion, {v: result.blocklist_version})}</Label> : null}
        </Card>
      ) : null}
    </Screen>
  );
}
