# Syncmatica third-party test server

Minimal in-memory HTTP server for local Syncmatica third-party mode testing.

## Run

```powershell
cd tserver
npm start
```

Default URL:

```text
http://127.0.0.1:8787
```

Configure the mod:

- `thirdParty.baseUrl`: `http://127.0.0.1:8787`
- `thirdParty.apiToken`: optional. If no `assignee` is sent, the server uses the token as the player name.

Data is stored in memory only and is cleared when the process exits.
