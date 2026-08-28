// Sends a chat push when a message row is inserted.
//
// Wired as a Supabase Database Webhook on messages INSERT, which is configured in the dashboard
// and carries a shared secret header. The secret is what is verified here, not the anon key:
// the anon key is public and would authenticate anyone.
import { createClient } from "jsr:@supabase/supabase-js@2";
import { sendChatPush } from "../_shared/fcm.ts";

const admin = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  { auth: { persistSession: false } },
);

// Notification copy is localised server-side. On API levels below 33 the per-app locale does
// not reach a background service, so localising in the client would be wrong for exactly the
// Hindi and Urdu users this app is built for.
const COPY = {
  en: (name: string) => ({ title: name, body: "sent you a message" }),
  hi: (name: string) => ({ title: name, body: "ne aapko message bheja hai" }),
  ur: (name: string) => ({ title: name, body: "نے آپ کو پیغام بھیجا ہے" }),
} as const;

Deno.serve(async (req) => {
  try {
    const secret = req.headers.get("x-webhook-secret");
    if (!secret || secret !== Deno.env.get("WEBHOOK_SECRET")) {
      return new Response("unauthorized", { status: 401 });
    }

    const payload = await req.json();
    const row = payload.record;
    if (!row?.conversation_id || !row?.sender_id) return new Response("ignored", { status: 204 });

    const { data: conv } = await admin
      .from("conversations")
      .select("user_a, user_b")
      .eq("id", row.conversation_id)
      .single();
    if (!conv) return new Response("no conversation", { status: 204 });

    const recipientId = conv.user_a === row.sender_id ? conv.user_b : conv.user_a;

    // A blocked sender's message is stored but never announced. Checking here as well as in RLS
    // matters because this runs as service_role, which RLS does not constrain.
    const { count: blocked } = await admin
      .from("blocks")
      .select("blocker_id", { count: "exact", head: true })
      .or(
        `and(blocker_id.eq.${recipientId},blocked_id.eq.${row.sender_id}),` +
        `and(blocker_id.eq.${row.sender_id},blocked_id.eq.${recipientId})`,
      );
    if ((blocked ?? 0) > 0) return new Response("blocked", { status: 204 });

    const [{ data: sender }, { data: recipient }, { data: tokens }] = await Promise.all([
      admin.from("profiles").select("display_name").eq("id", row.sender_id).single(),
      admin.from("profiles").select("locale").eq("id", recipientId).single(),
      admin.from("device_tokens").select("token").eq("user_id", recipientId),
    ]);

    if (!tokens?.length) return new Response("no devices", { status: 204 });

    const locale = (recipient?.locale ?? "hi") as keyof typeof COPY;
    const { title, body } = (COPY[locale] ?? COPY.hi)(sender?.display_name ?? "KabutarBaazi");

    const dead: string[] = [];
    await Promise.all(
      tokens.map(async ({ token }) => {
        const { deadToken } = await sendChatPush({
          token,
          title,
          body,
          conversationId: row.conversation_id,
          senderId: row.sender_id,
        });
        if (deadToken) dead.push(token);
      }),
    );

    if (dead.length) await admin.from("device_tokens").delete().in("token", dead);

    return new Response("sent", { status: 200 });
  } catch (e) {
    console.error(e);
    return new Response("error", { status: 500 });
  }
});
