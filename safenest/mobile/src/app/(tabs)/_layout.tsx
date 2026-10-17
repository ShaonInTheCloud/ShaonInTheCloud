import {Redirect, Tabs} from 'expo-router';
import {Glyph} from '@/components/ui';
import {useStore} from '@/lib/store';
import {fonts} from '@/lib/theme';

export default function TabsLayout() {
  const {t, theme, signedIn} = useStore();
  if (!signedIn) return <Redirect href="/" />;
  return (
    <Tabs screenOptions={{
      headerShown: false,
      sceneStyle: {backgroundColor: theme.bg},
      tabBarActiveTintColor: theme.pink,
      tabBarInactiveTintColor: theme.muted,
      tabBarStyle: {backgroundColor: theme.surface, borderTopColor: theme.line, height: 64, paddingTop: 6},
      tabBarLabelStyle: {fontFamily: fonts.bodySemi, fontSize: 12},
    }}>
      <Tabs.Screen name="index" options={{title: t.home, tabBarIcon: ({color}) => <Glyph name="home" color={color} />}} />
      <Tabs.Screen name="check" options={{title: t.check, tabBarIcon: ({color}) => <Glyph name="check" color={color} />}} />
      <Tabs.Screen name="plans" options={{title: t.plans, tabBarIcon: ({color}) => <Glyph name="plans" color={color} />}} />
      <Tabs.Screen name="help" options={{title: t.help, tabBarIcon: ({color}) => <Glyph name="help" color={color} />}} />
      <Tabs.Screen name="settings" options={{title: t.settings, tabBarIcon: ({color}) => <Glyph name="settings" color={color} />}} />
    </Tabs>
  );
}
