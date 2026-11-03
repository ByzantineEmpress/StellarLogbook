# EDSM + EDDiscovery setup walkthrough (~10 minutes, one time)

The app reads your visit history from **EDSM**. EDSM only knows what you upload
to it — the standard way is the free PC tool **EDDiscovery**, which watches your
journal files and syncs them automatically every time you play.

## Step 1 — EDSM account + commander (3 min)

1. Go to <https://www.edsm.net> → **Register** (free; email + password).
2. Log in, click your name (top right) → **My API Key** → copy the key (numbers + letters).
3. Click your name (top right) → **Commander** → create a commander:
   - Name: **exactly** your in-game name (e.g. `BYZANTINE EMPRESS`) — capitalisation matters.
   - Platform: **PC**.

## Step 2 — EDDiscovery (3 min)

1. Download the latest release: <https://github.com/EDDiscovery/EDDiscovery/releases>
   (installer exe). Install and launch.
2. Open the **Settings panel** (the settings icon in the top bar).
3. Go to the **Commanders** tab → select your commander → click **Edit**.
4. In the **EDSM section** of the commander dialog:
   - **EDSM commander name**: leave blank if it matches your in-game name, otherwise enter it.
   - **EDSM API key**: paste the key from Step 1.
5. Close Settings. Back on the main window, click **"Sync To"** (top area, next to your
   commander name) to upload your existing history to EDSM. New sessions upload
   automatically from now on.

## Step 3 — Verify (1 min)

**Easiest (no tools needed):** open this URL in your browser (replace NAME and
KEY, spaces are fine):

```
https://www.edsm.net/api-logs-v1/get-logs?commanderName=BYZANTINE EMPRESS&apiKey=PASTE-YOUR-API-KEY&startDateTime=2014-12-01&endDateTime=2030-01-01
```

- `"msgnum":100` + a `"logs":[ ... ]` list → **you're ready**; use the same name + key in the app.
- `"msgnum":203` → EDSM doesn't recognise the commander/key; re-check spelling and the API key page.
- `"msgnum":204` (rate limited) → wait a minute and reload.

**Or** with the bundled script (workspace has Node):

```powershell
& "C:\Users\Kyra\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe" tools\verify_edsm.mjs "BYZANTINE EMPRESS" "PASTE-YOUR-API-KEY"
```

## Alternatives

- **EDMC** (Market Connector) also uploads to EDSM: <https://github.com/EDCD/EDMarketConnector>.
- **No PC tool at all:** the app's journal-file import (Phase 2a) will read your
  journal files directly, and Phase 2b's "Relay" tool will automate that. EDSM
  remains the community data source either way.
