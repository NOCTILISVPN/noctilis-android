#!/usr/bin/env python3
"""Патч ядра sing-box перед сборкой libbox (22.09.2026).

Xray на наших узлах (REALITY с сентября 2026) требует в ClientHello ключевой обмен
X25519MLKEM768. sing-box в common/tls/reality_client.go намеренно вырезает эту группу из
supported_groups и key_share (см. sing-box issue #4520, открыт, починки нет) — из-за этого
«reality verification failed» на всех наших Reality-узлах при рабочих Hysteria2. Убираем
фильтр: uTLS-профиль Chrome сам шлёт MLKEM первым, как настоящий браузер.
Запуск: python3 patch-singbox.py <каталог исходников sing-box>
"""
import re
import sys

root = sys.argv[1]
path = root + "/common/tls/reality_client.go"
src = open(path, encoding="utf-8").read()

pattern = re.compile(
    r"\tfor _, extension := range uConn\.Extensions \{\n"
    r"\t\tif ce, ok := extension\.\(\*utls\.SupportedCurvesExtension\); ok \{\n"
    r"(?:.*\n)*?"
    r"\t\}\n"
    r"\terr = uConn\.BuildHandshakeState\(\)\n"
)
m = pattern.search(src)
if not m:
    sys.exit("патч: блок фильтра X25519MLKEM768 не найден — версия sing-box другая, сверить reality_client.go")
patched = src[: m.start()] + "\t// NOCTILIS: фильтр пост-квантовой группы в key_share убран (см. core/patch-singbox.py)\n\terr = uConn.BuildHandshakeState()\n" + src[m.end():]
if "X25519MLKEM768" in patched:
    sys.exit("патч: упоминание X25519MLKEM768 осталось — проверить вручную")
# common.Filter был единственным использованием пакета common — иначе «imported and not used»
if "common." not in patched.replace("sing/common", ""):
    patched = patched.replace('\t"github.com/sagernet/sing/common"\n', "", 1)

# 2) Версия клиента в session id. sing-box представляется как REALITY-клиент 1.8.1, а Xray
# с сентября 2026 отбрасывает старых клиентов как «устаревших». mihomo (PR #2983) шлёт
# версию Xray-core 26.7.11 — делаем так же. Убрать патч, когда sing-box починит #4520.
old_ver = "\thello.SessionId[0] = 1\n\thello.SessionId[1] = 8\n\thello.SessionId[2] = 1\n"
if old_ver not in patched:
    sys.exit("патч: строки версии клиента (1.8.1) не найдены — сверить reality_client.go")
patched = patched.replace(old_ver, "\t// NOCTILIS: версия клиента как у Xray-core 26.7.11 (см. core/patch-singbox.py)\n"
                                   "\thello.SessionId[0] = 26\n\thello.SessionId[1] = 7\n\thello.SessionId[2] = 11\n", 1)

# 3) Ключ для auth_key: если в приветствии только гибридный X25519MLKEM768 share, X25519-часть
# лежит в MlkemEcdhe (так делает mihomo).
old_key = "\tecdheKey := keyShareKeys.Ecdhe\n\tif ecdheKey == nil {\n\t\treturn nil, E.New(\"nil ecdheKey\")\n\t}\n"
if old_key not in patched:
    sys.exit("патч: блок ecdheKey не найден — сверить reality_client.go")
patched = patched.replace(old_key, "\tecdheKey := keyShareKeys.Ecdhe\n\tif ecdheKey == nil {\n\t\tecdheKey = keyShareKeys.MlkemEcdhe\n\t}\n"
                                   "\tif ecdheKey == nil {\n\t\treturn nil, E.New(\"nil ecdheKey\")\n\t}\n", 1)
open(path, "w", encoding="utf-8").write(patched)
print("патч наложен:", path)
