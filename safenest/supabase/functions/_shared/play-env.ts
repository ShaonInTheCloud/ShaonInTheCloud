export function playEnv() {
  return Object.fromEntries(['SUPABASE_URL','SUPABASE_ANON_KEY','SUPABASE_SERVICE_ROLE_KEY','SUPABASE_SECRET_KEYS',
    'PLAY_PACKAGE_NAME','PLAY_SERVICE_ACCOUNT_JSON','PLAY_RTDN_SECRET','PLAY_ALLOW_TEST_PURCHASES']
    .map(name => [name, Deno.env.get(name) ?? '']));
}
