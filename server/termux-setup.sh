#!/data/data/com.termux/files/usr/bin/bash
# Installs SearXNG inside Termux and creates ~/start-search.sh
set -e
pkg update -y && pkg upgrade -y
pkg install -y python git clang make rust binutils libxml2 libxslt openssl libffi

[ -d ~/searxng ] || git clone --depth 1 https://github.com/searxng/searxng ~/searxng
cd ~/searxng
python -m venv ~/sxvenv
source ~/sxvenv/bin/activate
pip install -U pip setuptools wheel pyyaml msgspec typing_extensions
pip install --use-pep517 --no-build-isolation -e .

SECRET=$(openssl rand -hex 32)
cat > ~/searxng-settings.yml <<YML
use_default_settings: true
server:
  secret_key: "$SECRET"
  bind_address: "127.0.0.1"
  port: 8888
  limiter: false
  image_proxy: false
search:
  formats: [html, json]
YML

cat > ~/start-search.sh <<'SH'
#!/data/data/com.termux/files/usr/bin/bash
termux-wake-lock
source ~/sxvenv/bin/activate
cd ~/searxng
SEARXNG_SETTINGS_PATH=~/searxng-settings.yml python -m searx.webapp
SH
chmod +x ~/start-search.sh

cat > ~/start-bg.sh <<'SH'
#!/data/data/com.termux/files/usr/bin/bash
pkill -f searx.webapp 2>/dev/null
nohup ~/start-search.sh > ~/search.log 2>&1 &
echo "Search server started in background"
SH
chmod +x ~/start-bg.sh
echo "Done. Start the server in the background with: ~/start-bg.sh"
echo "Then in the app set Search server URL to: http://127.0.0.1:8888"
