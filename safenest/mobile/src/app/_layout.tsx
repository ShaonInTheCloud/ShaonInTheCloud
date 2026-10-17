import {useEffect} from 'react';
import {Stack} from 'expo-router';
import {StatusBar} from 'expo-status-bar';
import * as SplashScreen from 'expo-splash-screen';
import {useFonts, AnekBangla_500Medium, AnekBangla_700Bold, AnekBangla_800ExtraBold} from '@expo-google-fonts/anek-bangla';
import {Manrope_400Regular, Manrope_600SemiBold, Manrope_700Bold} from '@expo-google-fonts/manrope';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {StoreProvider, useStore} from '@/lib/store';

SplashScreen.preventAutoHideAsync().catch(() => {});

function Root() {
  const {theme, ready} = useStore();
  useEffect(() => { if (ready) SplashScreen.hideAsync().catch(() => {}); }, [ready]);
  if (!ready) return null;
  return (
    <>
      <StatusBar style={theme.dark ? 'light' : 'dark'} />
      <Stack screenOptions={{headerShown: false, contentStyle: {backgroundColor: theme.bg}, animation: 'fade'}} />
    </>
  );
}

export default function Layout() {
  const [loaded] = useFonts({AnekBangla_500Medium, AnekBangla_700Bold, AnekBangla_800ExtraBold, Manrope_400Regular, Manrope_600SemiBold, Manrope_700Bold});
  if (!loaded) return null;
  return (
    <SafeAreaProvider>
      <StoreProvider>
        <Root />
      </StoreProvider>
    </SafeAreaProvider>
  );
}
