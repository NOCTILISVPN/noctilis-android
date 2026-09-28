#!/bin/bash
# Подписать APK постоянным ключом NOCTILIS на KZ (для сборок на компьютере Андрея — там ключа и пароля нет).
# sign-apk.sh <входной.apk> <выходной.apk>. Ключ: core/noctilis-keystore.p12.enc, пароль: /etc/techer/noctilis-keystore.pass.
set -euo pipefail
IN=$1; OUT=$2
APP=/root/projects/techer-app
BT=$(ls -d $APP/state/buildtools/android-* | head -1)
P12=$(mktemp --suffix=.p12); ALN=$(mktemp --suffix=.apk)
trap 'rm -f "$P12" "$ALN"' EXIT
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in $APP/core/noctilis-keystore.p12.enc -out "$P12" -pass file:/etc/techer/noctilis-keystore.pass
$BT/zipalign -f -p 4 "$IN" "$ALN"
$BT/apksigner sign --ks "$P12" --ks-type PKCS12 --ks-key-alias noctilis \
  --ks-pass file:/etc/techer/noctilis-keystore.pass \
  --out "$OUT" "$ALN"
$BT/apksigner verify --print-certs "$OUT" | grep -E "Signer #1 certificate (DN|SHA-256)"
