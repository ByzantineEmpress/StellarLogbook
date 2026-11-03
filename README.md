# Stellar Logbook

An unofficial Android companion app for Elite: Dangerous explorers. Tracks your visited
systems, your first discoveries, and — via an optional on-device **Uncharted Pack**
(Phase 2, no server anywhere) — shows systems you haven't visited and systems no
commander has reported visiting.

> Working name only; final branding must avoid Frontier/Elite trademarks. Community
> data comes from EDSM, Spansh and EDDN with attribution.

## Status

**Phase 0 (research + scaffold)** — complete. See [docs/phase0-findings.md](docs/phase0-findings.md).

**Phase 1 (MVP)** — code-complete in this repo (2026-11-03): EDSM sync engine with
pagination/checkpoints + MockWebServer unit tests, journal importer (visits, first
discoveries, mappings, codex) with idempotency tests, system detail screen with
bookmarks/notes and live EDSM community info, journal import UI (SAF picker),
encrypted API-key storage, auto-sync scheduling with Wi-Fi-only option, onboarding
help. **Pending:** first CI build (push to GitHub) and a real-account sync test.

## The four questions this app answers

| Question | App tab | Data source |
|---|---|---|
| Which systems have I visited? | Visited | EDSM flight logs (synced via EDDiscovery on your PC) |
| Which systems/bodies did *I* discover? | Discoveries | Your journal files (`Scan.WasDiscovered=false`, `CodexEntry`) |
| Which systems near me haven't I been to? | Explore | EDSM sphere-systems minus my visits (Phase 2) |
| Which systems has no player been to? | Explore | On-device gap search over the Uncharted Pack (Phase 2) |

## Stack

Kotlin 2 · Jetpack Compose (Material 3) · Room · Retrofit + kotlinx.serialization ·
WorkManager · DataStore · minSdk 26 / target 35.

## Build

Requires JDK 17 and the Android SDK. No Android SDK is available in the development
environment that generated this scaffold, so the first `./gradlew assembleDebug` run
(and the GitHub Actions CI) is the source of truth for compilation; if any pinned
version in `gradle/libs.versions.toml` has been yanked, bump it there.

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit tests (journal parser, galaxy math, DTOs)
```

## Module map

```
app/src/main/java/com/stellarlogbook/app/
├── core/          AppContainer (manual DI), GalaxyMath
├── data/local/    Room entities + DAOs (offline-first source of truth)
├── data/remote/   EDSM API (Retrofit), JournalParser
├── data/sync/     EdsmSyncEngine (pagination + checkpoint), EdsmSyncWorker
├── data/prefs/    UserPrefs (commander + API key via DataStore)
└── ui/            Compose screens, bottom-nav shell, onboarding
```

## Decisions locked (2026-11-03)

1. PC tools required for auto-sync: EDDiscovery (recommended) or EDMC → EDSM.
2. **No server.** The known-galaxy index is an optional ~135 MB **Uncharted Pack** on the
   phone (id64 + float32 coords + occupancy grid), built free by GitHub Actions from the
   EDSM nightly dump and hosted as a GitHub Release asset.
3. Journal files are the authoritative source for *your* discoveries.
4. Distribution: GitHub Releases APK for now.
5. Platform: PC (Live/Odyssey) only. No console support.

## Notes for Phase 2

- Batch-enrich EDSM-log-sourced systems with real `id64` (the detail screen already
  enriches each system on open via `api-v1/system`).
- Hilt is planned once the DI graph grows; the app uses a single AppContainer.
- Uncharted Pack pipeline + on-device gap search (plan §6), then the Phase 3 map.
