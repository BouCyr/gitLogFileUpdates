#!/usr/bin/env bash

echo "Remote: $1";
echo "Project: $2";


mkdir ./$2;
cd ./$2;

echo "Cloning $1";
git clone $1 . ;
#required for big project, and we want to target BIG targets :)
echo "Setting rename limit to 9999";
git config diff.renameLimit 9999 ;
echo "Extracting full git log in the expected format"
git log --pretty=medium --name-only > ./commits.txt;
echo "Launching analysis"
java ../../src/main/java/app/cbo/gitstats/GitStatsApplication.java ./commits.txt


cd ..

