#!/bin/bash
# Сборка APK NOCTILIS на временном сервере Timeweb и выкладка на noctilis.net.
# Запуск с KZ: build-local.sh <ip-сервера-сборки> <номер-версии, напр. 43>
# Код берётся из этой рабочей копии (не с GitHub), ядро libbox.aar — из state/core-cache,
# если уже собрано раньше. Пароль ключа — /etc/techer/noctilis-keystore.pass (на сервер
# копируется во время сборки и удаляется вместе с сервером). Заведено 28.09.2026.
set -euo pipefail
IP=$1; N=$2
APP=/root/projects/techer-app
SSH="ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR root@$IP"
RS="rsync -az -e 'ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR'"
mkdir -p $APP/state/core-cache
$SSH 'mkdir -p /root/build/src /root/build/core-cache'
eval $RS --delete --exclude state --exclude .git --exclude app/build --exclude .gradle $APP/ root@$IP:/root/build/src/
eval $RS $APP/state/core-cache/ root@$IP:/root/build/core-cache/
eval $RS /etc/techer/noctilis-keystore.pass root@$IP:/root/build/keystore.pass
$SSH "chmod 600 /root/build/keystore.pass; cp /root/build/src/bin/build-remote.sh /root/build/ && APP_VERSION_CODE=$N APP_VERSION_NAME=0.0.$N bash /root/build/build-remote.sh" 2>&1 | tee $APP/state/build-$N.log
eval $RS root@$IP:/root/build/core-cache/ $APP/state/core-cache/
eval $RS root@$IP:/root/build/noctilis-android-0.0.$N.apk $APP/state/
$SSH 'rm -f /root/build/keystore.pass'
ls -la $APP/state/noctilis-android-0.0.$N.apk
