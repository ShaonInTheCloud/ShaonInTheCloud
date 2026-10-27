// SafeNest "Solid Neon": flat solid hot pink, solid orange and neon magenta on deep plum.
// No gradients, no blurred glow. One look in light and dark system modes.
export type Theme = {
  dark: boolean;
  bg: string; surface: string; surfaceAlt: string; ink: string; muted: string; line: string;
  pink: string; pinkDeep: string; orange: string; neon: string; lavender: string;
  good: string; bad: string; onPink: string;
};
export function palette(_systemDark: boolean): Theme {
  return {
    dark: true,
    bg: '#0E0110', surface: '#1C0418', surfaceAlt: '#2A0824',
    ink: '#FFFFFF', muted: '#C9A7BF', line: '#3A0E33',
    pink: '#FF1F8F', pinkDeep: '#FF5CAE', orange: '#FF7A00', neon: '#FF2EE6', lavender: '#C9B4FF',
    good: '#5CF2B2', bad: '#FF5C7A', onPink: '#FFFFFF',
  };
}
// Revolut-style tight grotesk for display and UI; Anek Bangla wherever the text is Bangla.
export const fonts = {
  display: 'InterTight_800ExtraBold', displayBold: 'InterTight_700Bold',
  body: 'Inter_400Regular', bodySemi: 'Inter_600SemiBold', bodyBold: 'Inter_700Bold',
  bangla: 'AnekBangla_500Medium', banglaDisplay: 'AnekBangla_800ExtraBold',
};
export const radius = {card: 24, button: 16, input: 14};
