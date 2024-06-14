#!/usr/bin/env bash

./launch.sh https://github.com/facebook/react.git  REACT
mv ./REACT/top50_ALL.txt ./REACT.txt
mv ./REACT/ALL.CSV ./REACT.csv
rm -rf ./REACT

./launch.sh https://github.com/google/guava.git  GUAVA
mv ./GUAVA/top50_ALL.txt ./GUAVA.txt
mv ./GUAVA/ALL.csv ./GUAVA.csv
rm -rf ./GUAVA

./launch.sh https://github.com/keycloak/keycloak.git  KEYCLOAK
mv ./KEYCLOAK/top50_ALL.txt ./KEYCLOAK.txt
mv ./KEYCLOAK/ALL.csv ./KEYCLOAK.csv
rm -rf ./KEYCLOAK

./launch.sh https://github.com/openjdk/jdk.git JDK
mv ./JDK/top50_ALL.txt ./JDK.txt
mv ./JDK/ALL.csv ./JDK.csv
rm -rf ./JDK
