// SafeNest "Neon Nest": glossy hot pink and solar orange on night plum, with neon glow.
// One look in light and dark system modes: the glow needs the dark stage.
export type Theme = {
  dark: boolean;
  bg: string; surface: string; surfaceAlt: string; ink: string; muted: string; line: string;
  pink: string; pinkDeep: string; orange: string; neon: string; lavender: string;
  good: string; bad: string; onPink: string;
};
export function palette(_systemDark: boolean): Theme {
  return {
    dark: true,
    bg: '#14030F', surface: '#22061A', surfaceAlt: '#2E0A24',
    ink: '#FFF3F9', muted: '#C7A3B9', line: '#FF5CE12E',
    pink: '#FF1E8A', pinkDeep: '#FF6FB4', orange: '#FF8A1F', neon: '#FF5CE1', lavender: '#C9B4FF',
    good: '#5CF2B2', bad: '#FF5C7A', onPink: '#FFFFFF',
  };
}
/** Glossy candy gradient used for primary actions and the orb core. */
export const gloss = ['#FF3D9A', '#FF1E8A', '#FF8A1F'] as const;
export const fonts = {
  display: 'AnekBangla_800ExtraBold', displayBold: 'AnekBangla_700Bold',
  body: 'Manrope_400Regular', bodySemi: 'Manrope_600SemiBold', bodyBold: 'Manrope_700Bold',
  bangla: 'AnekBangla_500Medium',
};
export const radius = {card: 26, button: 999, input: 16};
