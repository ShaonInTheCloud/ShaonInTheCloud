// SafeNest brand: raspberry/hot pink on soft white by day, on plum night by night.
export type Theme = {
  dark: boolean;
  bg: string; surface: string; surfaceAlt: string; ink: string; muted: string; line: string;
  pink: string; pinkDeep: string; lavender: string; good: string; bad: string; onPink: string;
};
export function palette(dark: boolean): Theme {
  return dark ? {
    dark, bg: '#1a0b1f', surface: '#2a1033', surfaceAlt: '#36153f', ink: '#fff4f8', muted: '#c9b2c4', line: '#ffffff22',
    pink: '#ff2d87', pinkDeep: '#ff5fa3', lavender: '#c7b3ff', good: '#7ee7b8', bad: '#ff8aa8', onPink: '#ffffff',
  } : {
    dark, bg: '#fffafb', surface: '#ffffff', surfaceAlt: '#fff0f5', ink: '#2b1a2f', muted: '#7a6376', line: '#f0dbe4',
    pink: '#ff2d87', pinkDeep: '#c2185b', lavender: '#6f4fd8', good: '#1f8a5b', bad: '#b3123f', onPink: '#ffffff',
  };
}
export const fonts = {
  display: 'AnekBangla_800ExtraBold', displayBold: 'AnekBangla_700Bold',
  body: 'Manrope_400Regular', bodySemi: 'Manrope_600SemiBold', bodyBold: 'Manrope_700Bold',
  bangla: 'AnekBangla_500Medium',
};
export const radius = {card: 22, button: 999, input: 14};
