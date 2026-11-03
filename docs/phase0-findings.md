# Phase 0 Findings — 2026-11-03

Goal: verify every data-source assumption in the execution plan, parse real
journals, scaffold the Android project, and replace wrong assumptions with facts.
**Status: complete.** Everything below is verified live unless marked pending.

---

## 1. Environment

- This machine plays Elite: Dangerous (PC/Odyssey). **125 journal files**,
  2026-10-01 → 2026-11-03, commander **BYZANTINE EMPRESS**.
- Journals copied to `data/journals/` for the spike; full parse catalog in
  `data/spike/journal_catalog.json`, raw event examples in `data/spike/fixtures/`.
- No Java/Android SDK on this machine → **the scaffold compiles on GitHub Actions
  (CI), not locally**. Node (bundled) has full network access; PowerShell's .NET
  HTTP stack does not (local quirk — irrelevant to the app).

## 2. Journal spike (real data, October 2026)

| Stat | Value |
|---|---|
| Distinct systems visited | **1,233** |
| FSDJump events | 1,291 — **100% have StarPos coordinates** |
| Scan events | 10,092 |
| **First discoveries** (`Scan.WasDiscovered=false`) | **2,758** |
| **First mappings** (`Scan.WasMapped=false`) | **8,120** |
| CodexEntry | 256 (181 biology, 75 stellar bodies) |
| Coordinate extent | x −5,305…+1,708 · y −465…+199 · z −5,870…+7,960 (max jump-to-jump distance ~14,665 ly) |

**Confirmed decisions:**
- `Scan` + `CodexEntry` are the discovery source of record. The commander is an
  active explorer/exobiologist; there is plenty of real data to test with.
- **Odyssey journals do NOT populate `SAAScanComplete.Discovered[]/Mapped[]`**
  (313 events, 0 entries; fixture confirmed). Those arrays are Legacy-only.
  The parser must read `Scan.WasDiscovered/WasMapped` instead.
- Journal noise is huge (Music 14k, ShipLocker 11k events) — the parser keeps a
  whitelist: FSDJump, Location, CarrierJump, Scan, CodexEntry, SAAScanComplete.

## 3. EDSM (verified live)

| Endpoint | Result |
|---|---|
| `api-v1/system?systemName=Sol&showCoordinates=1&showInformation=1&showPrimaryStar=1&showPermit=1` | 200. `{name, coords{x,y,z}, coordsLocked, requirePermit, permitName, information{...}, primaryStar{type,name,isScoopable}}`. `information` also carries `discovered`/`discoveredBy` for known systems. |
| `api-v1/sphere-systems?systemName=Sol&radius=30&showCoordinates=1&showPrimaryStar=1&showId=1` | 200. Array of `{distance, bodyCount, name, id, id64, coords, coordsLocked, primaryStar}`. **Also observed: transient HTTP 503 `{}`** → retry/backoff is mandatory. |
| `api-logs-v1/get-logs` (no key) | HTTP 200 + `{"msgnum":203,"msg":"Commander name/API Key not found"}` → **errors come as msgnum ≠ 100 in a 200 body**; coded accordingly. |
| `api-v1/bodies?systemName=Sol` | 404 (HTML). **Per-system body lists should come from Spansh instead** (see §4). |
| Nightly dumps (`/dump/*.json.gz`) | `systemsPopulated` **529 MB**, `systemsWithCoordinates` **3.7 GB**, `systemsWithoutCoordinates` **16 MB**, `stations` **2.9 GB**, `bodies.json.gz` 404 (split elsewhere). |

## 4. Spansh (verified live)

| Endpoint | Result |
|---|---|
| `api/system/<id64>` (Sol = 10477373803) | 200. Rich JSON incl. per-body `distance_to_arrival`, `estimated_scan_value`, `estimated_mapping_value`, types — **ideal for the system-detail screen and value estimates**. |
| `api/systems` (POST, batch search) | Endpoint exists (200) but the request schema is not documented publicly; all guessed bodies returned `[]`. **Pending:** find the schema from the Spansh frontend/source in Phase 1, else use EDSM `api-v1/systems?systemName[]=` (verified endpoint shape in the edsm-rest repo). |
| Dumps (`downloads.spansh.co.uk`) | **Reality check:** `galaxy_1day.json.gz` 1.37 GB, `galaxy_1month` 5.6 GB, `galaxy.json.gz` **118.6 GB** (gzip!) — the dumps are now bodies-inclusive. **Unusable** for a systems-only known-set. Use EDSM dumps instead. |

## 5. Revised backend assumptions (Phase 2b implications)

Original plan assumed "SQLite on Render free tier, no sweat". The spike corrects that:

- **Known-systems source = EDSM `systemsWithCoordinates.json.gz` (3.7 GB).**
  Stream-decompress + incremental parse (no full-file landing) → compact SQLite
  `(id64 PK, name, x, y, z)` ≈ **1 GB final**, but needs ~5–15 GB scratch disk
  for the download/decompress pass. Render's free tier is too small.
- **Compact spatial index option:** id64 + 3×float32 = ~20 bytes/row ≈ **130 MB
  for ~6.5M systems** — small enough to consider an *on-device* gap search as a
  future alternative to the backend (kept in the back pocket).
- **Ranked hosting options** (decide at Phase 2b start, 1-day spike):
  1. **Self-host Docker on this gaming PC** — free, zero signup; app is
     offline-first so a PC-off window only degrades Explore/uncharted lookups.
  2. **Oracle Cloud Always Free ARM VM** — free forever, 200 GB disk, but card
     verification + signup friction.
  3. Paid micro-VPS (~€4/mo) as last resort.
- **Bodies:** Spansh per-system API on demand — no bodies dumps needed at all
  for the MVP feature set.

**Final decision (2026-11-03, owner confirmed no server available):** drop the
backend entirely. The float32 compaction insight makes the *whole known galaxy*
~130 MB, so it ships as an optional on-device **Uncharted Pack**: GitHub Actions
builds it monthly from the EDSM dump and publishes it as a GitHub Release asset;
the app downloads it on Wi-Fi and runs gap search locally. Named lookups stay on
EDSM/Spansh APIs. See the revised plan §6.

## 6. Scaffold status (`ed-companion/`)

- Kotlin 2.1 / Compose BOM 2024.12.01 / AGP 8.7.3 / Room 2.6.1 / Retrofit
  2.11.0 / WorkManager 2.10.0 / DataStore; minSdk 26, target 35; Gradle 8.11.1
  wrapper (official jar fetched from gradle/gradle).
- Offline-first Room schema **v1**: systems (PK=name — see below), bodies,
  jumps, discoveries, sync_checkpoints. Schema JSON exports on first build.
- Verified-shape EDSM Retrofit client + DTOs; journal parser (whitelist,
  tolerant) unit-tested against trimmed real fixtures.
- EDSM sync engine: 100/page pagination, datetime checkpoint, msgnum contract,
  no-progress guard; Worker with linear backoff.
- Compose shell: 5-tab bottom nav (Home/Visited/Discoveries/Explore/Map),
  onboarding (commander + API key), Home dashboard (current system, totals,
  one-tap SYNC), search-first Visited list, placeholders for Explore/Map.
- CI: GitHub Actions — JDK 17, assembleDebug + unit tests, APK artifact.
- **Schema decision:** EDSM flight logs identify systems by *name* (no id64),
  journals have id64 → **PK = name**, `id64` nullable enrichment column
  (enriched via EDSM systems batch in Phase 1).

**Not verified without the user's EDSM account (pending their 10-minute setup):**
`get-logs`/`get-position` with a real key, real pagination behavior over a large
history, and EDDiscovery upload mechanics. `tools/verify_edsm.mjs` covers the
first two once the account exists; walkthrough in `docs/setup-edsm-walkthrough.md`.

## 7. Assumptions replaced (plan deltas)

| Plan said | Reality (now) |
|---|---|
| Spansh galaxy dump = compact systems file | 118 GB bodies-inclusive; use EDSM systemsWithCoordinates |
| EDSM `bodies` endpoint for body lists | 404; use Spansh `/api/system/<id64>` |
| `SAAScanComplete.Discovered[]` as discovery source | Odyssey omits it; `Scan.WasDiscovered/WasMapped` + `CodexEntry` |
| Render free tier will host the backend | Too little disk for 3.7 GB ingestion; self-host / Oracle Always Free ranked first → **final: no backend at all (on-device pack)** |
| id64 as Room PK | Name as PK (EDSM logs lack id64); id64 nullable |
| Spansh batch search usable now | Endpoint exists, schema undocumented; EDSM `systemName[]` batch as fallback |

## 8. Phase 1 entry checklist

1. User completes EDSM/EDDiscovery setup (walkthrough) and runs `verify_edsm.mjs`.
2. Push `ed-companion/` to GitHub → CI compiles → fix any version-pin drift.
3. Exercise the sync engine against the real account; fix pagination/order
   merge logic (TODO flagged in `EdsmSyncEngine`).
4. Enrich systems with id64 (EDSM batch) + Spansh system detail screen.
5. Journal import (SAF picker) feeding the Discoveries tab; encrypted API-key storage.
6. Periodic WorkManager schedule + in-app setup walkthrough.
