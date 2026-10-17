import {useEffect} from 'react';
import {Stack} from 'expo-router';
import {StatusBar} from 'expo-status-bar';
import * as SplashScreen from 'expo-splash-screen';
import {useFonts, AnekBangla_500Medium, AnekBangla_700Bold, AnekBangla_800ExtraBold} from '@expo-google-fonts/anek-bangla';
import {InterTight_700Bold, InterTight_800ExtraBold} from '@expo-google-fonts/inter-tight';
import {Inter_400Regular, Inter_600SemiBold, Inter_700Bold} from '@expo-google-fonts/inter';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {Text, View} from 'react-native';
import {StoreProvider, useStore} from '@/lib/store';
import {useSafeAreaInsets} from 'react-native-safe-area-context';

SplashScreen.preventAutoHideAsync().catch(() => {});

function Root() {
  const {theme, ready, testMode, t} = useStore();
  const insets = useSafeAreaInsets();
  useEffect(() => { if (ready) SplashScreen.hideAsync().catch(() => {}); }, [ready]);
  if (!ready) return null;
  return (
    <>
      <StatusBar style={theme.dark ? 'light' : 'dark'} />
      <Stack screenOptions={{headerShown: false, contentStyle: {backgroundColor: theme.bg}, animation: 'fade'}} />
      {testMode ? (
        <View pointerEvents="none" style={{position: 'absolute', top: insets.top, alignSelf: 'center', backgroundColor: '#ffcc00', paddingHorizontal: 10, paddingVertical: 2, borderRadius: 999}}>
          <Text style={{fontSize: 11, fontWeight: '700', color: '#1a0b1f'}}>{t.testBanner}</Text>
        </View>
      ) : null}
    </>
  );
}

export default function Layout() {
  const [loaded] = useFonts({AnekBangla_500Medium, AnekBangla_700Bold, AnekBangla_800ExtraBold, InterTight_700Bold, InterTight_800ExtraBold, Inter_400Regular, Inter_600SemiBold, Inter_700Bold});
  if (!loaded) return null;
  return (
    <SafeAreaProvider>
      <StoreProvider>
        <Root />
      </StoreProvider>
    </SafeAreaProvider>
  );
}
