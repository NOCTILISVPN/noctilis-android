#!/bin/bash
# Андрей вводит пароль ключа подписи APK — он пишется в закрытый файл и нигде не показывается.
read -rsp "Введите пароль ключа подписи и нажмите Enter: " P; echo
[ -n "$P" ] || { echo "Пусто — ничего не записано"; exit 1; }
printf '%s' "$P" > /etc/techer/noctilis-keystore.pass && chmod 600 /etc/techer/noctilis-keystore.pass
unset P
echo "Записано. Можно закрыть терминал."
