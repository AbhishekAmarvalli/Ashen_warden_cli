@echo off
where java >/dev/null 2>/dev/null
java -jar "targetshen-warden.jar" --plain
