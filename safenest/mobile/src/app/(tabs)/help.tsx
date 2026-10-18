import {useState} from 'react';
import {Linking, Pressable, Text, View} from 'react-native';
import {Body, Card, Choice, Display, Label, Screen, Title} from '@/components/ui';
import {useStore} from '@/lib/store';
import {fonts, radius} from '@/lib/theme';
import {regions, sections, steps, type HelpContact, type HelpRegion} from '@/lib/help';

function Contact({c}: {c: HelpContact}) {
  const {theme, lang} = useStore();
  const open = () => { Linking.openURL(c.tel ? `tel:${c.tel}` : c.url!).catch(() => {}); };
  return (
    <Pressable accessibilityRole={c.tel ? 'button' : 'link'} onPress={open}
      accessibilityLabel={c.tel ? `${c.name}, call ${c.tel}` : c.name}
      style={({pressed}) => [{borderRadius: radius.input, borderWidth: 1, borderColor: theme.line, backgroundColor: theme.bg, padding: 14, gap: 4, opacity: pressed ? 0.8 : 1}]}>
      <View style={{flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: 10}}>
        <Text style={{fontFamily: fonts.bodyBold, fontSize: 17, color: theme.ink, flex: 1}}>{c.name}</Text>
        <Text style={{fontFamily: fonts.bodyBold, fontSize: 15, color: theme.pinkDeep}}>
          {c.tel ? (lang === 'bn' ? `কল ${c.tel}` : `Call ${c.tel}`) : (lang === 'bn' ? 'খুলুন ›' : 'Open ›')}
        </Text>
      </View>
      <Body size={14} muted>{c.detail[lang]}</Body>
    </Pressable>
  );
}

export default function Help() {
  const {lang, edition} = useStore();
  const [region, setRegion] = useState<HelpRegion>(edition === 'bd' ? 'bd' : 'intl');
  const t = (en: string, bn: string) => (lang === 'bn' ? bn : en);
  return (
    <Screen>
      <Display>{t('Help & support', 'সাহায্য ও সহায়তা')}</Display>
      <Body muted>{t('Gambling problems are common and treatable. You do not have to deal with this alone.',
        'জুয়ার সমস্যা খুবই সাধারণ এবং এর চিকিৎসা আছে। এটা একা সামলাতে হবে না।')}</Body>
      <View style={{gap: 8}}>
        <Label>{t('Where are you?', 'আপনি কোথায় আছেন?')}</Label>
        {regions.map(r => <Choice key={r.code} selected={region === r.code} title={r[lang]} onPress={() => setRegion(r.code)} />)}
      </View>
      {sections(region).map((s, i) => (
        <Card key={s.title.en} tone={i === 0 ? 'pink' : 'surface'}>
          <Title>{s.title[lang]}</Title>
          {s.body ? <Body size={15}>{s.body[lang]}</Body> : null}
          {s.contacts.map(c => <Contact key={c.name} c={c} />)}
        </Card>
      ))}
      <Card tone="alt">
        <Title>{t('Steps that help', 'যে পদক্ষেপগুলো সাহায্য করে')}</Title>
        {steps.map((s, i) => (
          <View key={i} style={{flexDirection: 'row', gap: 10}}>
            <Text style={{fontFamily: fonts.displayBold, fontSize: 16, width: 20}}>{i + 1}.</Text>
            <Body size={15} style={{flex: 1}}>{s[lang]}</Body>
          </View>
        ))}
      </Card>
      <Label>{t('SafeNest is not a medical or crisis service. Numbers checked October 2026; hours can change.',
        'SafeNest কোনো চিকিৎসা বা জরুরি সেবা নয়। নম্বরগুলো অক্টোবর ২০২৬-এ যাচাই করা; সময় বদলাতে পারে।')}</Label>
    </Screen>
  );
}
