// FCM v1 sender.
//
// Ported from local-tirri/apps/api/src/lib/fcm.ts, which deliberately avoids the firebase-admin
// dependency and does the service-account-JWT to OAuth2 exchange by hand. The same reasoning
// holds here: firebase-admin is a large dependency for one HTTP call.
import { create, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";

const projectId = Deno.env.get("FCM_PROJECT_ID")!;
const clientEmail = Deno.env.get("FCM_CLIENT_EMAIL")!;
// Secrets store the PEM with literal \n sequences, so they have to be turned back into newlines.
const privateKeyPem = (Deno.env.get("FCM_PRIVATE_KEY") ?? "").replace(/\\n/g, "\n");

let cachedToken: { value: string; expiresAt: number } | null = null;

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const body = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s/g, "");
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));
  return await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

/**
 * The module-level cache only survives a warm isolate, so a cold start pays roughly 150 ms for
 * a token fetch. That is latency, not extra invocations, and is not worth engineering around.
 */
async function accessToken(): Promise<string> {
  if (cachedToken && cachedToken.expiresAt > Date.now() + 60_000) return cachedToken.value;

  const key = await importPrivateKey(privateKeyPem);
  const assertion = await create(
    { alg: "RS256", typ: "JWT" },
    {
      iss: clientEmail,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      exp: getNumericDate(3600),
      // 60 seconds back, to tolerate clock skew between the isolate and Google.
      iat: getNumericDate(-60),
    },
    key,
  );

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  if (!res.ok) throw new Error(`oauth exchange failed: ${res.status} ${await res.text()}`);

  const body = await res.json();
  cachedToken = { value: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 };
  return cachedToken.value;
}

export interface ChatPush {
  token: string;
  title: string;
  body: string;
  conversationId: string;
  senderId: string;
}

/** Returns true when the token is dead and should be pruned. */
export async function sendChatPush(p: ChatPush): Promise<{ deadToken: boolean }> {
  const jwt = await accessToken();

  const res = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: { authorization: `Bearer ${jwt}`, "content-type": "application/json" },
      body: JSON.stringify({
        message: {
          token: p.token,
          // A notification block, unlike Tirri's data-only ride offers: for chat the tray
          // entry is the whole point when the app has been killed.
          notification: { title: p.title, body: p.body },
          android: {
            priority: "HIGH",
            ttl: "86400s",
            // collapseKey plus tag means one live notification per conversation, replaced
            // rather than stacked into a wall of entries.
            collapse_key: p.conversationId,
            notification: { channel_id: "chat", sound: "default", tag: p.conversationId },
          },
          data: {
            type: "chat",
            conversationId: p.conversationId,
            senderId: p.senderId,
          },
        },
      }),
    },
  );

  if (res.ok) return { deadToken: false };

  const text = await res.text();
  const dead = res.status === 404 || text.includes("UNREGISTERED") || text.includes("INVALID_ARGUMENT");
  if (!dead) console.error(`fcm send failed: ${res.status} ${text}`);
  return { deadToken: dead };
}
