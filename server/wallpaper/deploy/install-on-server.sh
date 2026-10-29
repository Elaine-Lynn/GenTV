#!/bin/sh
# All mutable production configuration is snapshotted before the first switch.
set -eu
lock=/var/tmp/gentv-wallpaper-deploy.lock
if ! mkdir "$lock" 2>/dev/null; then printf 'Another deployment holds the lock.\n' >&2; exit 1; fi
trap 'rmdir "$lock"' EXIT
archive=${1:?source archive required}
expected=${2:?SHA256 required}
printf '%s  %s\n' "$expected" "$archive" | sha256sum -c -
base=/srv/gentv/wallpaper
stamp=$(date -u +%Y%m%dT%H%M%S)-$$
release=$base/releases/$stamp
backup=/var/backups/gentv-wallpaper/$stamp
sudo -n install -d -m 755 "$release" "$base/media"
sudo -n install -d -m 700 "$backup" /var/backups/gentv-tv
sudo -n tar -xzf "$archive" -C "$release"
sudo -n python3 -m venv "$release/venv"
sudo -n "$release/venv/bin/pip" install -r "$release/requirements.txt"
sudo -n chmod -R a+rX "$release"
previous=$(readlink -f "$base/current")
files='/etc/gentv-wallpaper.env /etc/systemd/system/gentv-wallpaper.service /etc/systemd/system/gentv-retention.service /etc/systemd/system/gentv-retention.timer /etc/systemd/system/gentv-backup.service /etc/systemd/system/gentv-backup.timer /etc/systemd/system/gentv-health.service /etc/systemd/system/gentv-health.timer /etc/nginx/snippets/gentv-tv-api.locations.conf /etc/nginx/snippets/gentv-trusted-proxies.conf /etc/nginx/conf.d/gentv-limits.conf /etc/nginx/sites-available/api.gentv.app'
for timer in gentv-retention.timer gentv-backup.timer gentv-health.timer; do
  if sudo -n systemctl is-enabled --quiet "$timer"; then printf '%s\n' "$timer" | sudo -n tee -a "$backup/enabled" >/dev/null; fi
  if sudo -n systemctl is-active --quiet "$timer"; then printf '%s\n' "$timer" | sudo -n tee -a "$backup/active" >/dev/null; fi
done
for file in $files; do
  if sudo -n test -e "$file"; then sudo -n cp --parents "$file" "$backup"; else printf '%s\n' "$file" | sudo -n tee -a "$backup/absent" >/dev/null; fi
done
rollback() {
  code=$?
  trap - EXIT HUP INT TERM
  if [ "$code" -ne 0 ]; then
    for timer in gentv-retention.timer gentv-backup.timer gentv-health.timer; do sudo -n systemctl disable --now "$timer" || true; done
    for file in $files; do
      if sudo -n test -e "$backup$file"; then sudo -n cp "$backup$file" "$file"; else sudo -n rm -f "$file"; fi
    done
    sudo -n ln -sfn "$previous" "$base/current.rollback"
    sudo -n mv -Tf "$base/current.rollback" "$base/current"
    sudo -n systemctl daemon-reload
    if sudo -n test -f "$backup/enabled"; then sudo -n cat "$backup/enabled" | while IFS= read -r timer; do sudo -n systemctl enable "$timer"; done; fi
    if sudo -n test -f "$backup/active"; then sudo -n cat "$backup/active" | while IFS= read -r timer; do sudo -n systemctl start "$timer"; done; fi
    sudo -n systemctl restart gentv-wallpaper
    sudo -n nginx -t && sudo -n systemctl reload nginx
    printf 'Deployment failed; restored previous release and configuration.\n' >&2
  fi
  rmdir "$lock"
  exit "$code"
}
trap rollback EXIT
trap 'exit 1' HUP INT TERM
printf '%s\n' 'PUBLIC_BASE_URL=https://api.gentv.app/tv' 'WALLPAPER_DIR=/srv/gentv/wallpaper/media' | sudo -n tee /etc/gentv-wallpaper.env >/dev/null
for unit in gentv-wallpaper.service gentv-retention.service gentv-retention.timer gentv-backup.service gentv-backup.timer gentv-health.service gentv-health.timer; do sudo -n install -m 644 "$release/deploy/$unit" "/etc/systemd/system/$unit"; done
sudo -n install -m 644 "$release/deploy/nginx-wallpaper.locations.conf" /etc/nginx/snippets/gentv-tv-api.locations.conf
sudo -n python3 - <<'PYPROXY'
import ipaddress,json,subprocess
from pathlib import Path
payload=json.loads(subprocess.check_output(['curl','-fsS','--max-time','20','-A','GenTV-Deploy/1.0','https://api.cloudflare.com/client/v4/ips']))
if payload.get('success') is not True:raise RuntimeError('Trusted proxy list unavailable')
networks=[str(ipaddress.ip_network(n)) for field in ('ipv4_cidrs','ipv6_cidrs') for n in payload['result'][field]]
if len(networks)<10:raise RuntimeError('Incomplete trusted proxy list')
Path('/etc/nginx/snippets/gentv-trusted-proxies.conf').write_text(''.join('set_real_ip_from '+n+';\n' for n in networks)+'real_ip_header CF-Connecting-IP;\n')
Path('/etc/nginx/conf.d/gentv-limits.conf').write_text('limit_req_zone $binary_remote_addr zone=gentv_ingest:10m rate=5r/s;\n')
PYPROXY
sudo -n nginx -t
sudo -n ln -s "$release" "$base/current.next"
sudo -n mv -Tf "$base/current.next" "$base/current"
sudo -n systemctl daemon-reload
sudo -n systemctl restart gentv-wallpaper
curl -fsS --retry 5 --retry-connrefused --retry-delay 1 http://127.0.0.1:18765/tv/wallpaper >/dev/null
sudo -n systemctl reload nginx
curl -fsS --retry 3 https://api.gentv.app/tv/wallpaper >/dev/null
sudo -n systemctl start gentv-backup.service
sudo -n systemctl enable --now gentv-retention.timer gentv-backup.timer gentv-health.timer
sudo -n systemctl is-active gentv-wallpaper
trap - EXIT HUP INT TERM
rmdir "$lock"
printf 'release=%s\nrollback_snapshot=%s\n' "$release" "$backup"
