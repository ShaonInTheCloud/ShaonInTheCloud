#!/usr/bin/env bash
# One-time setup of the SafeNest blocklist checker on an Oracle Cloud Always Free VM (Ubuntu 22.04/24.04, ARM or x86).
# Run as the default "ubuntu" user:   curl -fsSL <raw url of this file> | bash      (or copy it over and run it)
set -euo pipefail
REPO=${SAFENEST_REPO:-https://github.com/ShaonInTheCloud/ShaonInTheCloud.git}
BRANCH=${SAFENEST_BRANCH:-safenest-global-card-crypto}
DIR=/opt/safenest

sudo apt-get update -y
sudo apt-get install -y git ca-certificates curl
if ! command -v node >/dev/null || [ "$(node -p 'process.versions.node.split(".")[0]')" -lt 22 ]; then
  curl -fsSL https://deb.nodesource.com/setup_22.x | sudo -E bash -
  sudo apt-get install -y nodejs
fi
sudo useradd --system --create-home --home-dir /var/lib/safenest --shell /usr/sbin/nologin safenest 2>/dev/null || true
sudo mkdir -p "$DIR" && sudo chown safenest:safenest "$DIR"
if [ ! -d "$DIR/.git" ]; then sudo -u safenest git clone --depth 1 --branch "$BRANCH" "$REPO" "$DIR"; fi
cd "$DIR/safenest/intelligence" && sudo -u safenest npm ci --omit=dev
cd "$DIR/safenest" && sudo -u safenest npm ci --omit=dev

if [ ! -f /etc/safenest-health.env ]; then
  sudo install -m 600 -o root -g root /dev/null /etc/safenest-health.env
  sudo tee /etc/safenest-health.env >/dev/null <<'ENV'
# Fill these in, then: sudo systemctl start safenest-health-seed.service
SUPABASE_URL=https://kflenmeizngmafwnwhgv.supabase.co
SUPABASE_SERVICE_ROLE_KEY=
HEALTH_CONCURRENCY=48
HEALTH_MAX_HOURS=20
ENV
fi
sudo cp "$DIR/safenest/intelligence/deploy/oracle/"safenest-health*.{service,timer} /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now safenest-health.timer
echo
echo "Done. Next:"
echo "  1. sudo nano /etc/safenest-health.env     (paste the Supabase service role key)"
echo "  2. sudo systemctl start safenest-health-seed.service   (one-time import, ~10 min)"
echo "  3. The daily check runs at 02:00 UTC. Logs: journalctl -u safenest-health -f"
