#!/bin/bash
# Сборка APK NOCTILIS на временном сервере (запускается НА сервере сборки, из /root/build).
# Повторяет .github/workflows/android.yml: ядро libbox из патченного sing-box, потом APK.
# Если libbox.aar уже лежит в /root/build/core-cache — ядро не пересобирается.
# Переменные: APP_VERSION_CODE, APP_VERSION_NAME. Пароль ключа — /root/build/keystore.pass.
set -euo pipefail
cd /root/build
SINGBOX_VERSION=v1.14.1
GOMOBILE_VERSION=v0.1.13
CORE_TAGS=with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api
export ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
export PATH=/usr/local/go/bin:/root/go/bin:/opt/gradle/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
# USE_PROXY=1: сервер в РФ — Google не отдаёт Android-инструменты на российские адреса, поэтому загрузки
# Google и Gradle идут через SOCKS 127.0.0.1:1080 (обратный туннель ssh -R 1080 с KZ, выход — Казахстан).
CURLPX=""; SDKPX=""; GRPX=""
if [ "${USE_PROXY:-0}" = 1 ]; then
  CURLPX="--proxy socks5h://127.0.0.1:1080"
  SDKPX="--proxy=socks --proxy_host=127.0.0.1 --proxy_port=1080"
  GRPX="-DsocksProxyHost=127.0.0.1 -DsocksProxyPort=1080"
fi
HAVE_CORE=0; [ -s core-cache/libbox.aar ] && HAVE_CORE=1

echo "== инструменты"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq && apt-get install -y -qq openjdk-17-jdk-headless unzip git python3 curl rsync >/dev/null
if [ "$HAVE_CORE" = 0 ] && [ ! -x /usr/local/go/bin/go ]; then
  curl -sfL https://go.dev/dl/go1.25.1.linux-amd64.tar.gz -o /tmp/go.tgz && tar -C /usr/local -xzf /tmp/go.tgz
fi
if [ ! -x $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager ]; then
  mkdir -p $ANDROID_HOME/cmdline-tools
  curl -sfL $CURLPX https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/clt.zip
  unzip -q /tmp/clt.zip -d $ANDROID_HOME/cmdline-tools && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
fi
yes | sdkmanager $SDKPX --licenses >/dev/null 2>&1 || true
PKGS=("platform-tools" "platforms;android-35" "build-tools;35.0.0"); [ "$HAVE_CORE" = 0 ] && PKGS+=("ndk;28.0.13004108")
sdkmanager $SDKPX --install "${PKGS[@]}" >/dev/null
if [ ! -x /opt/gradle/bin/gradle ]; then
  curl -sfL $CURLPX https://services.gradle.org/distributions/gradle-8.10.2-bin.zip -o /tmp/gradle.zip
  unzip -q /tmp/gradle.zip -d /opt && mv /opt/gradle-8.10.2 /opt/gradle
fi
[ "$HAVE_CORE" = 0 ] && go version; java -version 2>&1 | head -1

if [ ! -s core-cache/libbox.aar ]; then
  echo "== ядро sing-box $SINGBOX_VERSION (патч Reality/MLKEM)"
  rm -rf sing-box && git clone -q --depth 1 --branch $SINGBOX_VERSION https://github.com/SagerNet/sing-box.git sing-box
  python3 src/core/patch-singbox.py sing-box
  ( cd sing-box
    go install github.com/sagernet/gomobile/cmd/gomobile@$GOMOBILE_VERSION
    go install github.com/sagernet/gomobile/cmd/gobind@$GOMOBILE_VERSION
    CGO_ENABLED=0 go build -trimpath -tags "$CORE_TAGS" -o ../core-cache/sing-box-linux-amd64 ./cmd/sing-box
    go run ./cmd/internal/build_libbox -target android
    cp libbox.aar ../core-cache/libbox.aar )
fi
ls -la core-cache

echo "== APK $APP_VERSION_NAME ($APP_VERSION_CODE)"
mkdir -p src/app/libs && cp core-cache/libbox.aar src/app/libs/libbox.aar
openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -in src/core/noctilis-keystore.p12.enc -out /root/build/noctilis.p12 -pass file:/root/build/keystore.pass
export KEYSTORE_FILE=/root/build/noctilis.p12
export KEYSTORE_PASS="$(cat /root/build/keystore.pass)"
export APP_VERSION_CODE APP_VERSION_NAME
cd src && gradle $GRPX assembleRelease --no-daemon -q 2>&1 | tail -30
cp app/build/outputs/apk/release/app-release.apk /root/build/noctilis-android-$APP_VERSION_NAME.apk
rm -f /root/build/noctilis.p12
ls -la /root/build/*.apk
echo "== ГОТОВО"
