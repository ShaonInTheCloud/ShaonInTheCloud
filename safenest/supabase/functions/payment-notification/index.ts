import { handleNotification } from './handler.mjs';
Deno.serve((req: Request) => handleNotification(req, {
  SUPABASE_URL: Deno.env.get('SUPABASE_URL') ?? '',
  SUPABASE_SERVICE_ROLE_KEY: Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '',
}));
