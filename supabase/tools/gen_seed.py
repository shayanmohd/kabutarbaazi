#!/usr/bin/env python3
"""Generates 0005_seed.sql from the Kotlin catalogues in :domain.

Breed and region names live in exactly one place (Breed.kt / Region.kt) and this emits the SQL
seed from them. Re-run after editing either catalogue:

    python3 supabase/tools/gen_seed.py
"""
import re, pathlib, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
DOMAIN = ROOT / "domain/src/main/java/com/kabutarbaazi/domain/model"
OUT = ROOT / "supabase/migrations/0005_seed.sql"

ENTRY = re.compile(r'(?:Breed|Region)\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)')


def parse(path):
    text = path.read_text(encoding="utf-8")
    # Only the ALL list, so the data class declaration itself is not matched.
    body = text.split("val ALL", 1)[1]
    return ENTRY.findall(body)


def sql_escape(s):
    return s.replace("'", "''")


breeds = parse(DOMAIN / "Breed.kt")
regions = parse(DOMAIN / "Region.kt")

if not breeds or not regions:
    sys.exit("failed to parse catalogues")

lines = [
    "-- GENERATED FILE. Do not edit by hand.",
    "-- Source: domain/src/main/java/com/kabutarbaazi/domain/model/{Breed,Region}.kt",
    "-- Regenerate: python3 supabase/tools/gen_seed.py",
    "",
    "-- regions",
]
for i, (code, en, hi, ur) in enumerate(regions):
    lines.append(
        f"insert into regions (code, name_en, name_hi, name_ur, sort_order) values "
        f"('{sql_escape(code)}', '{sql_escape(en)}', '{sql_escape(hi)}', '{sql_escape(ur)}', {i})"
        f" on conflict (code) do update set name_en=excluded.name_en, name_hi=excluded.name_hi,"
        f" name_ur=excluded.name_ur, sort_order=excluded.sort_order;"
    )

lines += ["", "-- breeds"]
for i, (slug, en, hi, ur) in enumerate(breeds):
    lines.append(
        f"insert into breeds (slug, name_en, name_hi, name_ur, sort_order) values "
        f"('{sql_escape(slug)}', '{sql_escape(en)}', '{sql_escape(hi)}', '{sql_escape(ur)}', {i})"
        f" on conflict (slug) do update set name_en=excluded.name_en, name_hi=excluded.name_hi,"
        f" name_ur=excluded.name_ur, sort_order=excluded.sort_order;"
    )

lines += [
    "",
    "-- One community per region, admin-seeded. Users cannot create groups in v1.",
    "insert into communities (region_code, slug, name_en, name_hi, name_ur)",
    "select r.code, r.code, r.name_en, r.name_hi, r.name_ur from regions r",
    "on conflict (region_code) do nothing;",
    "",
]

OUT.write_text("\n".join(lines), encoding="utf-8")
print(f"wrote {OUT.relative_to(ROOT)}: {len(regions)} regions, {len(breeds)} breeds")
