#!/bin/bash
# Андрей вводит токен Timeweb с полным доступом (для временного сервера сборки APK).
# Пишется в закрытый файл /etc/techer/timeweb-build.env, на экран не выводится.
read -rsp "Вставьте токен Timeweb и нажмите Enter: " T; echo
[ -n "$T" ] || { echo "Пусто — ничего не записано"; exit 1; }
printf 'TIMEWEB_TOKEN=%s\n' "$T" > /etc/techer/timeweb-build.env && chmod 600 /etc/techer/timeweb-build.env
unset T
echo "Записано. Можно закрыть терминал."
