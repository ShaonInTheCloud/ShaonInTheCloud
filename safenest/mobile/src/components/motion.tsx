import React, {useEffect, useRef, useState} from 'react';
import {AccessibilityInfo, Animated, Easing, StyleSheet, View, useWindowDimensions} from 'react-native';
import Svg, {Circle, Defs, Ellipse, LinearGradient as SvgLinear, RadialGradient, Stop} from 'react-native-svg';

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

function useLoop(duration: number, run: boolean) {
  const value = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    if (!run) { value.stopAnimation(); return; }
    const loop = Animated.loop(Animated.timing(value, {toValue: 1, duration, easing: Easing.linear, useNativeDriver: true}));
    loop.start();
    return () => loop.stop();
  }, [run, duration, value]);
  return value;
}

/** Slow aurora of pink, orange and magenta light drifting behind a screen. */
export function Aurora() {
  const reduced = useReducedMotion();
  const {width, height} = useWindowDimensions();
  const t = useLoop(26000, !reduced);
  const blob = (color: string, size: number, x: [number, number], y: [number, number], opacity: number, key: string) => {
    const translateX = t.interpolate({inputRange: [0, 0.5, 1], outputRange: [x[0], x[1], x[0]]});
    const translateY = t.interpolate({inputRange: [0, 0.5, 1], outputRange: [y[0], y[1], y[0]]});
    return (
      <Animated.View key={key} pointerEvents="none" style={{position: 'absolute', width: size, height: size, opacity, transform: [{translateX}, {translateY}]}}>
        <Svg width={size} height={size}>
          <Defs>
            <RadialGradient id={key} cx="50%" cy="50%" r="50%">
              <Stop offset="0" stopColor={color} stopOpacity="0.9" />
              <Stop offset="1" stopColor={color} stopOpacity="0" />
            </RadialGradient>
          </Defs>
          <Circle cx={size / 2} cy={size / 2} r={size / 2} fill={`url(#${key})`} />
        </Svg>
      </Animated.View>
    );
  };
  const s = Math.max(width, 360) * 1.1;
  return (
    <View pointerEvents="none" style={StyleSheet.absoluteFill}>
      {blob('#FF1E8A', s, [-s * 0.45, -s * 0.2], [-s * 0.4, -s * 0.25], 0.42, 'aPink')}
      {blob('#FF8A1F', s * 0.85, [width - s * 0.5, width - s * 0.7], [height * 0.35, height * 0.2], 0.22, 'aOrange')}
      {blob('#FF5CE1', s * 0.8, [-s * 0.2, s * 0.05], [height * 0.7, height * 0.55], 0.18, 'aNeon')}
    </View>
  );
}

/**
 * The Nest Orb: a glossy pink-to-orange core inside two counter-rotating neon rings.
 * Breathes while protection is on; dims to plum when it is off.
 */
export function NestOrb({size = 220, active}: {size?: number; active: boolean}) {
  const reduced = useReducedMotion();
  const spin = useLoop(14000, !reduced);
  const breathe = useLoop(3600, !reduced && active);
  const rotate = spin.interpolate({inputRange: [0, 1], outputRange: ['0deg', '360deg']});
  const counter = spin.interpolate({inputRange: [0, 1], outputRange: ['360deg', '0deg']});
  const scale = breathe.interpolate({inputRange: [0, 0.5, 1], outputRange: [1, 1.045, 1]});
  const glow = breathe.interpolate({inputRange: [0, 0.5, 1], outputRange: [0.45, 0.85, 0.45]});
  const c = size / 2;
  const core = active ? ['#FFB3D6', '#FF1E8A', '#FF8A1F'] : ['#7A5A70', '#4A2440', '#2E0A24'];
  const ringA = active ? '#FF5CE1' : '#6B4A62';
  const ringB = active ? '#FF8A1F' : '#4F3049';
  return (
    <View style={{width: size, height: size, alignItems: 'center', justifyContent: 'center'}} accessible={false}>
      <Animated.View style={[StyleSheet.absoluteFill, {opacity: active ? glow : 0.25}]}>
        <Svg width={size} height={size}>
          <Defs>
            <RadialGradient id="halo" cx="50%" cy="50%" r="50%">
              <Stop offset="0.45" stopColor={active ? '#FF1E8A' : '#4A2440'} stopOpacity="0.7" />
              <Stop offset="1" stopColor={active ? '#FF1E8A' : '#4A2440'} stopOpacity="0" />
            </RadialGradient>
          </Defs>
          <Circle cx={c} cy={c} r={c} fill="url(#halo)" />
        </Svg>
      </Animated.View>
      <Animated.View style={[StyleSheet.absoluteFill, {transform: [{rotate}]}]}>
        <Svg width={size} height={size}>
          <Defs>
            <SvgLinear id="ringA" x1="0" y1="0" x2="1" y2="1">
              <Stop offset="0" stopColor={ringA} stopOpacity="1" />
              <Stop offset="1" stopColor={ringA} stopOpacity="0.05" />
            </SvgLinear>
          </Defs>
          <Circle cx={c} cy={c} r={c * 0.86} stroke="url(#ringA)" strokeWidth={3} fill="none" strokeDasharray={`${c * 1.6} ${c * 0.5}`} strokeLinecap="round" />
        </Svg>
      </Animated.View>
      <Animated.View style={[StyleSheet.absoluteFill, {transform: [{rotate: counter}]}]}>
        <Svg width={size} height={size}>
          <Circle cx={c} cy={c} r={c * 0.74} stroke={ringB} strokeOpacity={0.8} strokeWidth={2} fill="none" strokeDasharray={`${c * 0.22} ${c * 0.16}`} />
        </Svg>
      </Animated.View>
      <Animated.View style={{transform: [{scale}]}}>
        <Svg width={size * 0.56} height={size * 0.56}>
          <Defs>
            <RadialGradient id="core" cx="35%" cy="30%" r="75%">
              <Stop offset="0" stopColor={core[0]} />
              <Stop offset="0.45" stopColor={core[1]} />
              <Stop offset="1" stopColor={core[2]} />
            </RadialGradient>
          </Defs>
          <Circle cx={size * 0.28} cy={size * 0.28} r={size * 0.28} fill="url(#core)" />
          <Ellipse cx={size * 0.22} cy={size * 0.15} rx={size * 0.11} ry={size * 0.06} fill="#FFFFFF" fillOpacity={active ? 0.55 : 0.2} />
        </Svg>
      </Animated.View>
    </View>
  );
}
