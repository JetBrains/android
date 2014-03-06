#!/bin/sh

# The script accepts one parameter, which can be "init", "start" or "finish"
# Before first use run the script with "init" parameter
# To get the changes from AOSP repository do following:
# 1. Run the script with "start" parameter
# 2. Perform "pull" from AOSP remote for "android" and "tools-base" repositories using IntelliJ IDEA,
#    resolve all conflicts, commit, check that everything is compiled correctly and the tests are passed
# 3. Run the script with "finish" parameter

if [ "$1" = "init" ]; then
  git remote add AOSP https://android.googlesource.com/platform/tools/adt/idea
  cd tools-base
  git remote add AOSP https://android.googlesource.com/platform/tools/base
elif [ "$1" = "start" ]; then
  git branch -d android-tmp
  git checkout -b android-tmp
  cd tools-base
  git branch -d android-tmp
  git checkout -b android-tmp
elif [ "$1" = "finish" ]; then
  git checkout master
  git merge android-tmp
  cd tools-base
  git checkout master
  git merge android-tmp
else
  echo 'Unknown parameter'
fi