@echo off
cd /d C:\Users\soranzo\IdeaProjects\chronostore
call mvn package
scp -i C:\Users\soranzo\.ssh\id_ed25519 target\chronostore-1.0-SNAPSHOT.jar root@192.168.0.28:/root/server_vanilla/data/plugins/
echo Deploy finalizado.
pause