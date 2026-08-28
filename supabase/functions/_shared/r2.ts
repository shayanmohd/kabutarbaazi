// Cloudflare R2 presigning.
//
// aws4fetch rather than a hand-rolled SigV4 implementation: signing is easy to get subtly wrong
// and impossible to debug from the client side, where the only symptom is an opaque 403.
import { AwsClient } from "npm:aws4fetch@1.0.20";

const accountId = Deno.env.get("R2_ACCOUNT_ID")!;
const bucket = Deno.env.get("R2_BUCKET")!;
const publicBase = Deno.env.get("R2_PUBLIC_BASE")!; // https://kb-media.kamapathy.app

const client = new AwsClient({
  accessKeyId: Deno.env.get("R2_ACCESS_KEY_ID")!,
  secretAccessKey: Deno.env.get("R2_SECRET_ACCESS_KEY")!,
  service: "s3",
  region: "auto",
});

const endpoint = () => `https://${accountId}.r2.cloudflarestorage.com/${bucket}`;

/**
 * Signs a PUT valid for five minutes.
 *
 * Content-Type and Content-Length are part of the signature, so a client cannot upload a
 * different type or a larger file than the one the server approved. Without that, the size caps
 * would be a suggestion.
 */
export async function presignPut(
  key: string,
  contentType: string,
  contentLength: number,
): Promise<{ putUrl: string; publicUrl: string; expiresAt: string }> {
  const url = new URL(`${endpoint()}/${key}`);
  url.searchParams.set("X-Amz-Expires", "300");

  const signed = await client.sign(
    new Request(url, {
      method: "PUT",
      headers: {
        "content-type": contentType,
        "content-length": String(contentLength),
        // Keys are content-unique, so nothing ever needs revalidating.
        "cache-control": "public, max-age=31536000, immutable",
      },
    }),
    { aws: { signQuery: true } },
  );

  return {
    putUrl: signed.url,
    publicUrl: `${publicBase}/${key}`,
    expiresAt: new Date(Date.now() + 300_000).toISOString(),
  };
}

/** Deletes objects. Used by admin "remove" and by account deletion. */
export async function deleteObjects(keys: string[]): Promise<void> {
  await Promise.all(
    keys.map(async (key) => {
      const res = await client.fetch(`${endpoint()}/${key}`, { method: "DELETE" });
      if (!res.ok && res.status !== 404) {
        console.error(`failed to delete ${key}: ${res.status}`);
      }
    }),
  );
}

/** Lists every object under a prefix, following continuation tokens. */
export async function listPrefix(prefix: string): Promise<string[]> {
  const keys: string[] = [];
  let token: string | undefined;
  do {
    const url = new URL(endpoint());
    url.searchParams.set("list-type", "2");
    url.searchParams.set("prefix", prefix);
    if (token) url.searchParams.set("continuation-token", token);

    const res = await client.fetch(url.toString());
    if (!res.ok) throw new Error(`list failed: ${res.status}`);
    const xml = await res.text();

    for (const m of xml.matchAll(/<Key>([^<]+)<\/Key>/g)) keys.push(m[1]);
    const next = xml.match(/<NextContinuationToken>([^<]+)<\/NextContinuationToken>/);
    token = next?.[1];
  } while (token);
  return keys;
}

export { publicBase };
