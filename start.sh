#!/usr/bin/env bash

git pull --rebase origin master

mvn clean package

java -jar target/nowlisteningbot-*-with-dependencies.jar

