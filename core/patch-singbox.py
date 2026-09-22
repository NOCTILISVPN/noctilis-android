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
open(path, "w", encoding="utf-8").write(patched)
print("патч наложен:", path)
