@echo off
set /p x=READTEST
echo GOT=%x%
java -jar "target\ashen-warden.jar" --plain
