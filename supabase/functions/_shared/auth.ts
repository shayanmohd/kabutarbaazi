// Shared JWT verification. Every function that touches user data starts here.
import { createClient, SupabaseClient } from "jsr:@supabase/supabase-js@2";

export interface Caller {
  id: string;
  admin: SupabaseClient;
}

/**
 * Verifies the caller's JWT by asking Supabase who it belongs to, and returns a service_role
 * client for the work that follows.
 *
 * The user id comes from the verified token, never from the request body. Every object key and
 * every ownership check downstream is built from this value, which is what stops one user
 * writing into another user's storage prefix.
 */
export async function requireUser(req: Request): Promise<Caller> {
  const authHeader = req.headers.get("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) throw new HttpError(401, "missing_token");

  const url = Deno.env.get("SUPABASE_URL")!;
  const anon = Deno.env.get("SUPABASE_ANON_KEY")!;
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

  const asUser = createClient(url, anon, {
    global: { headers: { Authorization: authHeader } },
    auth: { persistSession: false },
  });

  const { data, error } = await asUser.auth.getUser();
  if (error || !data.user) throw new HttpError(401, "invalid_token");

  const admin = createClient(url, serviceKey, { auth: { persistSession: false } });
  return { id: data.user.id, admin };
}

export class HttpError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

export function errorResponse(e: unknown): Response {
  if (e instanceof HttpError) return json({ error: e.message }, e.status);
  console.error(e);
  return json({ error: "internal_error" }, 500);
}
