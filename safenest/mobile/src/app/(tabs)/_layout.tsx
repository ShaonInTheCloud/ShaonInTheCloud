import {Redirect, Tabs} from 'expo-router';
import {View} from 'react-native';
import {Glyph} from '@/components/ui';
import {useStore} from '@/lib/store';
import {fonts} from '@/lib/theme';

export default function TabsLayout() {
  const {t, theme, signedIn, lang} = useStore();
  if (!signedIn) return <Redirect href="/" />;
  return (
    <Tabs screenOptions={{
      headerShown: false,
      sceneStyle: {backgroundColor: theme.bg},
      tabBarActiveTintColor: theme.neon,
      tabBarInactiveTintColor: theme.muted,
      // Floating glass bar; screens leave room for it at the bottom.
      tabBarStyle: {position: 'absolute', left: 16, right: 16, bottom: 18, height: 74, paddingTop: 10, paddingBottom: 10, borderRadius: 37,
        backgroundColor: theme.surface, borderTopWidth: 0, borderWidth: 0, elevation: 0},
      tabBarItemStyle: {borderRadius: 26},
      tabBarBackground: () => <View style={{flex: 1, borderRadius: 37, backgroundColor: theme.surface}} />,
      tabBarLabelStyle: {fontFamily: fonts.bodySemi, fontSize: 11},
    }}>
      <Tabs.Screen name="index" options={{title: t.home, tabBarIcon: ({color}) => <Glyph name="home" color={color} />}} />
      <Tabs.Screen name="check" options={{title: lang === 'bn' ? 'যাচাই' : 'Check', tabBarIcon: ({color}) => <Glyph name="check" color={color} />}} />
      <Tabs.Screen name="plans" options={{title: t.plans, tabBarIcon: ({color}) => <Glyph name="plans" color={color} />}} />
      <Tabs.Screen name="help" options={{title: t.help, tabBarIcon: ({color}) => <Glyph name="help" color={color} />}} />
      <Tabs.Screen name="settings" options={{title: t.settings, tabBarIcon: ({color}) => <Glyph name="settings" color={color} />}} />
    </Tabs>
  );
}
