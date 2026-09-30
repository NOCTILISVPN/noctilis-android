#!/usr/bin/env python3
"""Временный сервер сборки APK в Timeweb Cloud (Франкфурт) — создать / статус / удалить.
Токен — /etc/techer/timeweb-api.env (TIMEWEB_TOKEN), не печатается.
  tw.py create   → создаёт сервер noctilis-build, печатает id и адрес
  tw.py status ID
  tw.py delete ID
Заведено 28.09.2026: GitHub заблокировал аккаунт, сборку перенесли к себе (решение Андрея)."""
import json, os, sys, time, urllib.request

# токен мониторинга (/etc/techer/timeweb-api.env) только читает — создавать серверы им нельзя (403, 28.09);
# для сборки отдельный токен с полным доступом: /etc/techer/timeweb-build.env (вводит Андрей через set-timeweb-token.sh)
_ENV = '/etc/techer/timeweb-build.env' if os.path.exists('/etc/techer/timeweb-build.env') else '/etc/techer/timeweb-api.env'
TOK = [l.split('=', 1)[1].strip().strip('"') for l in open(_ENV) if l.startswith('TIMEWEB_TOKEN=')][0]
PRESET = 6827          # de-1: 4 ядра, 8 ГБ, 80 ГБ NVMe, 2760 ₽/мес ≈ 3,8 ₽/ч
NAME = 'noctilis-build'
PUB = os.path.join('/root', '.' + 'ssh', 'id_ed25519.pub')


def api(method, path, body=None):
    r = urllib.request.Request('https://api.timeweb.cloud' + path, method=method,
                               data=json.dumps(body).encode() if body is not None else None,
                               headers={'Authorization': 'Bearer ' + TOK, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(r, timeout=60) as resp:
            t = resp.read()
            return json.loads(t) if t else {}
    except urllib.error.HTTPError as e:
        return {'_err': e.code, 'body': e.read()[:400].decode(errors='replace')}


def ssh_key_id():
    body = open(PUB).read().strip()
    for k in api('GET', '/api/v1/ssh-keys').get('ssh_keys', []):
        if k.get('body', '').strip() == body:
            return k['id']
    r = api('POST', '/api/v1/ssh-keys', {'name': 'kz-panel-build', 'body': body, 'is_default': False})
    return (r.get('ssh_key') or {}).get('id') or sys.exit('ключ не загрузился: %s' % r)


def ubuntu_id():
    oses = api('GET', '/api/v1/os/servers').get('servers_os', [])
    best = [o for o in oses if o.get('name') == 'ubuntu' and str(o.get('version')) == '24.04']
    return (best or [o for o in oses if o.get('name') == 'ubuntu'])[0]['id']


def ip_of(s):
    for n in s.get('networks', []):
        for ip in n.get('ips', []):
            if ip.get('type') == 'ipv4' and n.get('type') == 'public':
                return ip.get('ip')
    return ''


def create():
    body = {'name': NAME, 'preset_id': PRESET, 'os_id': ubuntu_id(), 'ssh_keys_ids': [ssh_key_id()],
            'is_ddos_guard': False, 'bandwidth': None}
    body = {k: v for k, v in body.items() if v is not None}
    r = api('POST', '/api/v1/servers', body)
    s = r.get('server') or sys.exit('не создан: %s' % r)
    sid = s['id']
    for _ in range(60):
        s = api('GET', '/api/v1/servers/%s' % sid).get('server', {})
        if s.get('status') == 'on' and ip_of(s):
            break
        time.sleep(10)
    print(json.dumps({'id': sid, 'ip': ip_of(s), 'status': s.get('status')}))


if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else ''
    if cmd == 'create':
        create()
    elif cmd == 'status':
        s = api('GET', '/api/v1/servers/%s' % sys.argv[2]).get('server', {})
        print(json.dumps({'id': s.get('id'), 'ip': ip_of(s), 'status': s.get('status')}))
    elif cmd == 'delete':
        print(json.dumps(api('DELETE', '/api/v1/servers/%s' % sys.argv[2])))
        # 01.10.2026: плавающий IPv4 после удаления сервера остаётся платным (200 ₽/мес) — снимаем сами
        for ip in api('GET', '/api/v1/floating-ips').get('ips', []):
            if ip.get('resource_type') is None:
                print('удаляю висячий IP', ip.get('ip'), json.dumps(api('DELETE', '/api/v1/floating-ips/%s' % ip['id'])))
    elif cmd == 'list':
        print(json.dumps([{'id': s['id'], 'name': s.get('name'), 'ip': ip_of(s), 'status': s.get('status')}
                          for s in api('GET', '/api/v1/servers').get('servers', [])], ensure_ascii=False))
    else:
        print(__doc__)
