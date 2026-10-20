@echo off
chcp 65001 >/dev/null
set /p x=READTEST
echo GOT=%x%
java -jar "targetshen-warden.jar" --plain
