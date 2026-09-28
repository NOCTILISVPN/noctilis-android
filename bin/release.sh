#!/bin/bash
# Полный выпуск версии APK NOCTILIS без GitHub: временный сервер Timeweb → сборка → удаление
# сервера → проверка подписи против раздаваемой версии → выкладка на noctilis.net.
# Запуск: release.sh <номер версии, напр. 44>. Сервер удаляется ВСЕГДА, даже при ошибке.
set -uo pipefail
N=$1
APP=/root/projects/techer-app
DST=/opt/stack/caddy/static/noctilis
cd $APP/bin

SERVER=$(ZONE=${ZONE:-} python3 - <<'EOF'
import tw, json, time, sys
import os
PRESETS = (6751, 6761) if os.environ.get('ZONE') == 'ru' else (6813, 6827, 6829)   # ru: Екатеринбург/Казань 4/8 ≈2,5 ₽/ч; иначе Амстердам/Франкфурт
for preset in PRESETS:
    r = tw.api('POST', '/api/v1/servers', {'name': tw.NAME, 'preset_id': preset, 'os_id': tw.ubuntu_id(),
                                           'ssh_keys_ids': [tw.ssh_key_id()], 'is_ddos_guard': False})
    if 'server' in r:
        break
    print('preset %s: %s' % (preset, r.get('body', '')[:100]), file=sys.stderr)
else:
    sys.exit(1)
sid = r['server']['id']
for _ in range(60):
    s = tw.api('GET', '/api/v1/servers/%s' % sid).get('server', {})
    if s.get('status') == 'on':
        break
    time.sleep(10)
if not tw.ip_of(s):   # зарубежные серверы Timeweb создаются только с IPv6 — докупаем IPv4
    for attempt in range(3):
        r4 = tw.api('POST', '/api/v1/servers/%s/ips' % sid, {'type': 'ipv4'})
        print('ipv4: %s' % json.dumps(r4)[:200], file=sys.stderr)
        if 'server_ip' in r4:
            break
        time.sleep(20)
    for _ in range(30):
        s = tw.api('GET', '/api/v1/servers/%s' % sid).get('server', {})
        if tw.ip_of(s):
            break
        time.sleep(10)
print(json.dumps({'id': sid, 'ip': tw.ip_of(s)}))
EOF
) || { echo "сервер не создан"; exit 1; }
echo "$SERVER" > $APP/state/build-server.json
SID=$(python3 -c "import json,sys;print(json.loads(sys.argv[1])['id'])" "$SERVER")
IP=$(python3 -c "import json,sys;print(json.loads(sys.argv[1])['ip'])" "$SERVER")
trap 'python3 $APP/bin/tw.py delete $SID >/dev/null; echo "сервер $SID удалён"' EXIT
echo "сервер сборки: $IP"
[ -n "$IP" ] || { echo "нет адреса IPv4 — сборку не начинаю"; exit 1; }
for i in $(seq 1 30); do ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR -o ConnectTimeout=8 -o BatchMode=yes root@$IP true && break; sleep 10; done

USE_PROXY=$([ "${ZONE:-}" = ru ] && echo 1 || echo 0) bash $APP/bin/build-local.sh "$IP" "$N" || { echo "СБОРКА УПАЛА"; exit 1; }
A=$APP/state/noctilis-android-0.0.$N.apk
[ -s "$A" ] || { echo "нет APK"; exit 1; }

# подпись должна совпадать с раздаваемой версией — иначе обновление поверх не встанет
python3 - "$DST/noctilis.apk" "$A" <<'EOF' || { echo "ПОДПИСЬ НЕ СОВПАДАЕТ — не выкладываю"; exit 1; }
import hashlib, subprocess, sys
def certs(path):
    d = open(path, 'rb').read(); i = d.rfind(b'APK Sig Block 42'); blk = d[max(0, i - 200000):i]; out = set(); j = 0
    while True:
        j = blk.find(b'\x30\x82', j)
        if j < 0: break
        der = blk[j:j + int.from_bytes(blk[j + 2:j + 4], 'big') + 4]
        r = subprocess.run(['openssl', 'x509', '-inform', 'DER', '-noout', '-subject'], input=der, capture_output=True)
        if r.returncode == 0 and b'NOCTILIS' in r.stdout: out.add(hashlib.sha256(der).hexdigest())
        j += 2
    return out
a, b = certs(sys.argv[1]), certs(sys.argv[2])
sys.exit(0 if a and a == b else 1)
EOF

cp "$A" $DST/noctilis.apk.new && mv $DST/noctilis.apk.new $DST/noctilis.apk && chmod 644 $DST/noctilis.apk
printf '{"version":"0.0.%s","url":"https://noctilis.net/noctilis.apk","size":%s}\n' "$N" "$(stat -c %s "$A")" > $DST/noctilis-version.json
echo "v0.0.$N-local" > $APP/state/apk-tag
echo "$(date '+%F %T') noctilis.apk обновлён: 0.0.$N (release.sh, Timeweb)" >> $APP/state/apk-sync.log
echo "ВЫЛОЖЕНО: 0.0.$N"
