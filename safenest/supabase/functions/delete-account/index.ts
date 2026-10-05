import { createClient } from 'npm:@supabase/supabase-js@2.117.1';
import { deletionHandler } from './handler.mjs';

const options = { auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false } };
const url = Deno.env.get('SUPABASE_URL')!;
const anonKey = Deno.env.get('SUPABASE_ANON_KEY')!;
const serviceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
// These runtime secrets stay on Supabase; no admin credential enters the browser or APK.
const user = createClient(url, anonKey, options);
const admin = createClient(url, serviceKey, options);
Deno.serve(deletionHandler({
  userAuth: user.auth,
  // A new client per request avoids sharing mutable sign-in state between users.
  passwordAuth: { signInWithPassword: (credentials) => createClient(url, anonKey, options).auth.signInWithPassword(credentials) },
  adminAuth: admin.auth.admin
}));
