import React, {useEffect, useRef, useState} from 'react';
import {AccessibilityInfo, Animated, Easing, StyleSheet, Text, View, type StyleProp, type TextStyle} from 'react-native';
import Svg, {Circle, Ellipse} from 'react-native-svg';

/** True when the phone asks for reduced motion; animations then hold still. */
export function useReducedMotion() {
  const [reduced, setReduced] = useState(false);
  useEffect(() => {
    AccessibilityInfo.isReduceMotionEnabled().then(setReduced).catch(() => {});
    const sub = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduced);
    return () => sub.remove();
  }, []);
  return reduced;
}

function useLoop(duration: number, run: boolean, easing = Easing.linear) {
  const value = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    if (!run) { value.stopAnimation(); value.setValue(0); return; }
    const loop = Animated.loop(Animated.timing(value, {toValue: 1, duration, easing, useNativeDriver: true}));
    loop.start();
    return () => loop.stop();
  }, [run, duration, value, easing]);
  return value;
}

/** Kept for screens that still render it: the background is now a flat solid colour. */
export function Aurora() { return null; }

/**
 * The Nest Orb, flat: a solid hot-pink core with one gloss highlight, a solid orange segmented ring and a neon
 * ring, turning in opposite directions. The core pulses while protection is on; everything turns plum when off.
 */
export function NestOrb({size = 220, active}: {size?: number; active: boolean}) {
  const reduced = useReducedMotion();
  const spin = useLoop(12000, !reduced);
  const pulse = useLoop(2400, !reduced && active, Easing.inOut(Easing.quad));
  const rotate = spin.interpolate({inputRange: [0, 1], outputRange: ['0deg', '360deg']});
  const counter = spin.interpolate({inputRange: [0, 1], outputRange: ['360deg', '0deg']});
  const scale = pulse.interpolate({inputRange: [0, 0.5, 1], outputRange: [1, 1.06, 1]});
  const c = size / 2;
  const core = active ? '#FF1F8F' : '#3A1033';
  const orange = active ? '#FF7A00' : '#5A2A50';
  const neon = active ? '#FF2EE6' : '#4A2342';
  return (
    <View style={{width: size, height: size, alignItems: 'center', justifyContent: 'center'}} accessible={false}>
      <Animated.View style={[StyleSheet.absoluteFill, {transform: [{rotate}]}]}>
        <Svg width={size} height={size}>
          <Circle cx={c} cy={c} r={c * 0.92} stroke={neon} strokeWidth={4} fill="none" strokeDasharray={`${c * 2.2} ${c * 0.7}`} strokeLinecap="round" />
        </Svg>
      </Animated.View>
      <Animated.View style={[StyleSheet.absoluteFill, {transform: [{rotate: counter}]}]}>
        <Svg width={size} height={size}>
          <Circle cx={c} cy={c} r={c * 0.78} stroke={orange} strokeWidth={8} fill="none" strokeDasharray={`${c * 0.34} ${c * 0.16}`} />
        </Svg>
      </Animated.View>
      <Animated.View style={{transform: [{scale}]}}>
        <Svg width={size * 0.56} height={size * 0.56}>
          <Circle cx={size * 0.28} cy={size * 0.28} r={size * 0.28} fill={core} />
          <Ellipse cx={size * 0.2} cy={size * 0.14} rx={size * 0.08} ry={size * 0.045} fill="#FFFFFF" fillOpacity={active ? 0.9 : 0.25} />
        </Svg>
      </Animated.View>
    </View>
  );
}

/** Revolut-style rolling number: counts up to `value` when it first appears or changes. */
export function CountUp({value, format, style}: {value: number; format: (n: number) => string; style?: StyleProp<TextStyle>}) {
  const reduced = useReducedMotion();
  const anim = useRef(new Animated.Value(0)).current;
  const [shown, setShown] = useState(reduced ? value : 0);
  useEffect(() => {
    if (reduced) { setShown(value); return; }
    const id = anim.addListener(({value: v}) => setShown(v));
    Animated.timing(anim, {toValue: value, duration: 900, easing: Easing.out(Easing.cubic), useNativeDriver: false}).start();
    return () => anim.removeListener(id);
  }, [value, reduced, anim]);
  return <Text style={style}>{format(shown)}</Text>;
}
