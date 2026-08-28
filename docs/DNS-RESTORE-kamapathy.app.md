# kamapathy.app DNS snapshot, taken before the Cloudflare migration

Captured 2026-08-28, while nameservers were still at name.com. This is the restore reference:
if Cloudflare's automatic scan misses a record, re-create it by hand from this table.

| Type | Name | Value | Proxy setting on Cloudflare |
|---|---|---|---|
| A | `kamapathy.app` (apex) | `76.76.21.21` | **DNS only (grey cloud)** — Vercel |
| CNAME | `www` | `cname.vercel-dns.com` | **DNS only (grey cloud)** — Vercel |
| CNAME | `api` | `kamapathy-api.onrender.com` | **DNS only (grey cloud)** — Render |
| MX | `kamapathy.app` | `inbound-smtp.ap-northeast-1.amazonaws.com` priority `10` | n/a — **AWS SES inbound mail** |
| TXT | `_dmarc` | `v=DMARC1; p=none;` | n/a — mail policy |

Original nameservers at name.com:
```
ns1psw.name.com
ns2glx.name.com
ns3flt.name.com
ns4qxz.name.com
```

## Why proxying must stay OFF for the three web records

Vercel and Render both terminate TLS themselves and issue their own certificates. Putting
Cloudflare's proxy (orange cloud) in front of them produces redirect loops or certificate
errors. The migration only moves *where DNS is answered*; traffic must keep going straight to
Vercel and Render exactly as before.

## The MX record is the dangerous one

`inbound-smtp.ap-northeast-1.amazonaws.com` is AWS SES receiving inbound mail for the domain.
It is invisible in a browser, so a broken import shows up as email silently disappearing rather
than as a page that fails to load. Verify it explicitly after the cutover:

```bash
dig +short MX kamapathy.app     # must return: 10 inbound-smtp.ap-northeast-1.amazonaws.com.
```

## Rollback

Set the nameservers at name.com back to the four `name.com` servers listed above. Propagation is
typically under an hour. The records above are still authoritative at name.com until that zone is
deleted, so a rollback restores the previous state without needing to re-enter anything.
