@echo off
rem Duplo clique: volta o servidor para o banco de PRODUCAO (reinicia o WildFly se estiver no ar).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0banco.ps1" producao
pause
