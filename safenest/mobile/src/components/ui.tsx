import React from 'react';
import {ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, TextInput, View, type ColorValue, type PressableProps,
  type StyleProp, type TextInputProps, type TextStyle, type ViewStyle} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {LinearGradient} from 'expo-linear-gradient';
import * as Haptics from 'expo-haptics';
import {useStore} from '@/lib/store';
import {fonts, gloss, radius} from '@/lib/theme';
import {Aurora} from '@/components/motion';

export function Screen({children, scroll = true, style}: {children: React.ReactNode; scroll?: boolean; style?: StyleProp<ViewStyle>}) {
  const {theme} = useStore();
  const insets = useSafeAreaInsets();
  const padding = {paddingTop: insets.top + 16, paddingBottom: insets.bottom + 110, paddingLeft: Math.max(20, insets.left), paddingRight: Math.max(20, insets.right)};
  if (!scroll) return <View style={{flex: 1, backgroundColor: theme.bg}}><Aurora /><View style={[{flex: 1}, padding, style]}>{children}</View></View>;
  return (
    <View style={{flex: 1, backgroundColor: theme.bg}}>
      <Aurora />
      <ScrollView style={{flex: 1}} contentContainerStyle={[padding, {gap: 18}, style]}
        keyboardShouldPersistTaps="handled" contentInsetAdjustmentBehavior="never">
        {children}
      </ScrollView>
    </View>
  );
}

export function Display({children, style, size = 34}: {children: React.ReactNode; style?: StyleProp<TextStyle>; size?: number}) {
  const {theme, lang} = useStore();
  return <Text style={[{fontFamily: lang === 'bn' ? fonts.display : fonts.display, fontSize: size, lineHeight: size * 1.04, color: theme.ink, letterSpacing: -size * 0.025}, style]}>{children}</Text>;
}
export function Title({children, style}: {children: React.ReactNode; style?: StyleProp<TextStyle>}) {
  const {theme} = useStore();
  return <Text style={[{fontFamily: fonts.displayBold, fontSize: 22, lineHeight: 28, color: theme.ink}, style]}>{children}</Text>;
}
export function Body({children, muted, style, size = 16}: {children: React.ReactNode; muted?: boolean; style?: StyleProp<TextStyle>; size?: number}) {
  const {theme, lang} = useStore();
  return <Text style={[{fontFamily: lang === 'bn' ? fonts.bangla : fonts.body, fontSize: size, lineHeight: size * 1.55, color: muted ? theme.muted : theme.ink}, style]}>{children}</Text>;
}
export function Label({children, style}: {children: React.ReactNode; style?: StyleProp<TextStyle>}) {
  const {theme} = useStore();
  return <Text style={[{fontFamily: fonts.bodySemi, fontSize: 13, lineHeight: 18, color: theme.muted}, style]}>{children}</Text>;
}

/** Dark glass with a neon gradient edge; 'pink' is the glossy candy surface for the one thing that matters most. */
export function Card({children, style, tone = 'surface'}: {children: React.ReactNode; style?: StyleProp<ViewStyle>; tone?: 'surface' | 'alt' | 'pink'}) {
  const {theme} = useStore();
  if (tone === 'pink') return (
    <LinearGradient colors={[...gloss]} start={{x: 0, y: 0}} end={{x: 1, y: 1}}
      style={[{borderRadius: radius.card, padding: 22, gap: 10, shadowColor: theme.pink, shadowOpacity: 0.55, shadowRadius: 24, shadowOffset: {width: 0, height: 10}, elevation: 10}, style]}>
      <LinearGradient pointerEvents="none" colors={['#FFFFFF55', '#FFFFFF00']} style={{position: 'absolute', left: 1, right: 1, top: 1, height: '45%', borderTopLeftRadius: radius.card, borderTopRightRadius: radius.card}} />
      {children}
    </LinearGradient>
  );
  return (
    <LinearGradient colors={tone === 'alt' ? ['#FF8A1F66', '#FF5CE122'] : ['#FF5CE155', '#FFFFFF10', '#FF8A1F33']} start={{x: 0, y: 0}} end={{x: 1, y: 1}}
      style={[{borderRadius: radius.card, padding: 1}]}>
      <View style={[{backgroundColor: tone === 'alt' ? '#2E0A24' : '#22061A', borderRadius: radius.card - 1, padding: 20, gap: 10}, style]}>{children}</View>
    </LinearGradient>
  );
}

type ButtonProps = PressableProps & {label: string; kind?: 'primary' | 'ghost' | 'text'; busy?: boolean; style?: StyleProp<ViewStyle>};
/** Primary: glossy candy pill with a highlight and pink glow. Ghost: neon outline. Text: inline link. */
export function Button({label, kind = 'primary', busy, disabled, style, onPress, ...rest}: ButtonProps) {
  const {theme} = useStore();
  const base: ViewStyle = {minHeight: 56, borderRadius: radius.button, paddingHorizontal: 24, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 10, overflow: 'hidden'};
  const color = kind === 'primary' ? theme.onPink : kind === 'ghost' ? theme.ink : theme.pinkDeep;
  const press: PressableProps['onPress'] = e => { if (kind === 'primary') Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light).catch(() => {}); onPress?.(e); };
  const content = (
    <>
      {busy ? <ActivityIndicator color={color} /> : null}
      <Text style={{fontFamily: fonts.displayBold, fontSize: 18, color, includeFontPadding: false, letterSpacing: 0.2}}>{label}</Text>
    </>
  );
  return (
    <Pressable accessibilityRole="button" disabled={disabled || busy} onPress={press} {...rest}
      style={({pressed}) => [{borderRadius: radius.button, opacity: disabled ? 0.45 : 1, transform: [{scale: pressed ? 0.97 : 1}]},
        kind === 'primary' ? {shadowColor: theme.pink, shadowOpacity: 0.6, shadowRadius: 18, shadowOffset: {width: 0, height: 8}, elevation: 8} : null, style]}>
      {kind === 'primary' ? (
        <LinearGradient colors={[...gloss]} start={{x: 0, y: 0}} end={{x: 1, y: 1}} style={base}>
          <LinearGradient pointerEvents="none" colors={['#FFFFFF66', '#FFFFFF00']} style={{position: 'absolute', left: 0, right: 0, top: 0, height: '52%'}} />
          {content}
        </LinearGradient>
      ) : (
        <View style={[base, kind === 'ghost' ? {borderWidth: 1.5, borderColor: theme.neon, backgroundColor: '#22061A'} : {minHeight: 44, paddingHorizontal: 8}]}>{content}</View>
      )}
    </Pressable>
  );
}

export function Field(props: TextInputProps & {label: string; hint?: string}) {
  const {theme} = useStore();
  const {label, hint, style, ...rest} = props;
  return (
    <View style={{gap: 6}}>
      <Label>{label}</Label>
      <TextInput placeholderTextColor={theme.muted} {...rest}
        style={[{fontFamily: fonts.body, fontSize: 17, color: theme.ink, backgroundColor: '#2A0820', borderWidth: 1, borderColor: theme.line,
          borderRadius: radius.input, paddingHorizontal: 16, paddingVertical: 14}, style]} />
      {hint ? <Label>{hint}</Label> : null}
    </View>
  );
}

export function Choice({selected, title, note, onPress}: {selected: boolean; title: string; note?: string; onPress: () => void}) {
  const {theme} = useStore();
  return (
    <Pressable accessibilityRole="radio" accessibilityState={{selected}} onPress={onPress}
      style={({pressed}) => [{flexDirection: 'row', alignItems: 'center', gap: 14, padding: 16, borderRadius: 18, borderWidth: 2,
        borderColor: selected ? theme.neon : theme.line, backgroundColor: selected ? '#3A0B2C' : '#22061A', opacity: pressed ? 0.85 : 1,
        shadowColor: theme.neon, shadowOpacity: selected ? 0.5 : 0, shadowRadius: 12}]}>
      <View style={{width: 22, height: 22, borderRadius: 11, borderWidth: 2, borderColor: selected ? theme.pink : theme.muted, alignItems: 'center', justifyContent: 'center'}}>
        {selected ? <View style={{width: 11, height: 11, borderRadius: 6, backgroundColor: theme.pink}} /> : null}
      </View>
      <View style={{flex: 1, gap: 2}}>
        <Text style={{fontFamily: fonts.bodyBold, fontSize: 17, color: theme.ink}}>{title}</Text>
        {note ? <Label>{note}</Label> : null}
      </View>
    </Pressable>
  );
}

export function Pill({text, tone}: {text: string; tone: 'good' | 'bad' | 'neutral' | 'pink'}) {
  const {theme} = useStore();
  const color = tone === 'good' ? theme.good : tone === 'bad' ? theme.bad : tone === 'pink' ? theme.pink : theme.muted;
  return (
    <View style={{alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 12, paddingVertical: 6, borderRadius: 999, borderWidth: 1.5, borderColor: color}}>
      <View style={{width: 8, height: 8, borderRadius: 4, backgroundColor: color}} />
      <Text style={{fontFamily: fonts.bodyBold, fontSize: 13, color}}>{text}</Text>
    </View>
  );
}

export function Notice({text, tone = 'neutral'}: {text: string; tone?: 'neutral' | 'bad' | 'good'}) {
  const {theme} = useStore();
  if (!text) return null;
  const color = tone === 'bad' ? theme.bad : tone === 'good' ? theme.good : theme.ink;
  return (
    <View style={{borderLeftWidth: 3, borderLeftColor: color, paddingLeft: 12, paddingVertical: 4}} accessibilityLiveRegion="polite">
      <Body size={15} style={{color}}>{text}</Body>
    </View>
  );
}

// Tab glyphs drawn from Views so the app needs no icon font.
export function Glyph({name, color: tint, size = 22}: {name: 'home' | 'check' | 'plans' | 'help' | 'settings'; color: ColorValue | null | undefined; size?: number}) {
  const color = (typeof tint === 'string' ? tint : undefined) ?? '#7a6376';
  const s = size, b = StyleSheet.hairlineWidth * 4;
  if (name === 'home') return (
    <View style={{width: s, height: s, alignItems: 'center', justifyContent: 'flex-end'}}>
      <View style={{position: 'absolute', top: 0, width: s * 0.7, height: s * 0.7, borderWidth: b, borderColor: color, transform: [{rotate: '45deg'}], borderBottomWidth: 0, borderRightWidth: 0}} />
      <View style={{width: s * 0.72, height: s * 0.5, borderWidth: b, borderColor: color, borderTopWidth: 0, borderRadius: 3}} />
    </View>);
  if (name === 'check') return (
    <View style={{width: s, height: s}}>
      <View style={{width: s * 0.68, height: s * 0.68, borderRadius: s, borderWidth: b, borderColor: color}} />
      <View style={{position: 'absolute', right: 1, bottom: 1, width: s * 0.42, height: b, backgroundColor: color, transform: [{rotate: '45deg'}], borderRadius: 2}} />
    </View>);
  if (name === 'plans') return (
    <View style={{width: s, height: s, justifyContent: 'center'}}>
      <View style={{width: s, height: s * 0.68, borderWidth: b, borderColor: color, borderRadius: 5}} />
      <View style={{position: 'absolute', top: s * 0.38, width: s, height: b, backgroundColor: color}} />
    </View>);
  if (name === 'help') return (
    <View style={{width: s, height: s, alignItems: 'center', justifyContent: 'center'}}>
      <View style={{width: s * 0.9, height: s * 0.9, borderRadius: s, borderWidth: b, borderColor: color, alignItems: 'center', justifyContent: 'center'}}>
        <Text style={{color, fontSize: s * 0.55, fontWeight: '800', lineHeight: s * 0.62, includeFontPadding: false}}>?</Text>
      </View>
    </View>);
  return (
    <View style={{width: s, height: s, alignItems: 'center', justifyContent: 'center'}}>
      <View style={{width: s * 0.9, height: s * 0.9, borderRadius: s, borderWidth: b, borderColor: color, borderStyle: 'dashed'}} />
      <View style={{position: 'absolute', width: s * 0.34, height: s * 0.34, borderRadius: s, borderWidth: b, borderColor: color}} />
    </View>);
}
