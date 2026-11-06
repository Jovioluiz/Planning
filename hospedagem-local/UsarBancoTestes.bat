@echo off
rem Duplo clique: passa o servidor para o banco de TESTES (reinicia o WildFly se estiver no ar).
rem Para comecar com uma copia da producao: powershell -File banco.ps1 testes -CopiarProducao
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0banco.ps1" testes
pause
