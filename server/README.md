# Your SearXNG search server

1. Get any small Linux server (a VPS, Oracle Cloud free tier, or a home PC) with Docker installed.
2. Copy this `server/` folder to it and edit `searxng/settings.yml`: set `secret_key` to `openssl rand -hex 32`.
3. Run `docker compose up -d`.
4. Test: open `http://SERVER_IP:8080/search?q=test&format=json` in a browser; you should see JSON.
5. In the app: Settings -> Search server URL -> `http://SERVER_IP:8080`.

Security: this server answers anyone who finds it. Restrict port 8080 with a firewall to your own IP, or put it behind HTTPS and a password (Caddy/nginx) before sharing the address.

## Running on your Android phone (no laptop)
Install **Termux from F-Droid** (not the Play Store version), then run:
`pkg install -y curl && curl -fsSL https://raw.githubusercontent.com/ankitgoelgopal-dotcom/Bhanurepo/main/server/termux-setup.sh | bash`
Start it with `~/start-search.sh` and set the app's Search server URL to `http://127.0.0.1:8888`.
