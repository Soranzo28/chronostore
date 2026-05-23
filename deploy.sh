#!/bin/bash
cd ~/IdeaProjects/chronostore
mvn package && scp -i ~/.ssh/id_ed25519 target/chronostore-1.0-SNAPSHOT.jar root@100.101.245.18:/root/server_vanilla/data/plugins/
