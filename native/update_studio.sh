#!/bin/bash

# Manual updater for Android Studio.
#
# Android Studio has an integrated update mechanism which is the recommended
# way to update Studio. Some users have experienced issues with applying updates
# and in this case the Android Studio team might ask you to try to manually
# update using this script. If you use this please make sure to give us some
# feedback by reporting a bug on b.android.com.
#
# This script figures out the current Studio build and the latest canary
# available on the server. It downloads the corresponding patch and tries to
# apply it.
#
# Variables you might want to modify:
# - FROM: the build-number to update from (in the form 123.456789)
# - TO  : the build-number to update to   (in the form 123.456789)
# - JAVA_PATH: The path to your java executable.
#
# IMPORTANT: to execute this:
# - On Linux, copy the script into the android-studio/bin directory
#   (the bin directory where studio.sh is located) and execute it from a shell:
#   $ path/to/android-studio/bin/update_studio.sh
# - On MacOS X, copy the script into the Android Studio.app/bin directory
#   (from the Finder, right-click on the Android Studio icon > Show Package Content,
#    you will find the bin directory in there.) then open a Terminal and execute:
#   $ ~/Applications/Android Studio.app/bin/update_studio.sh

IS_MAC=""
if [[ "Darwin" == $(uname) ]]; then IS_MAC="1"; fi

# Change current directory and drive to parent of where the script is.
STUDIO_DIR=$(dirname "$0")
STUDIO_DIR=$(dirname "$STUDIO_DIR")
if [[ -x $(which realpath) ]]; then
  STUDIO_DIR=$(realpath "$STUDIO_DIR")
elif [[ "$STUDIO_DIR" != /* ]]; then
  STUDIO_DIR="$PWD/$STUDIO_DIR"
fi
cd "$STUDIO_DIR"

BIN=bin/studio.sh
if [[ -n $IS_MAC ]]; then BIN=Contents/MacOS/studio ; fi
if [[ ! -x "$BIN" ]]; then
  echo "This does not look like an Android Studio directory."
  echo "Please place this script in your android-studio/bin"
  echo "directory and try again."
  exit 1
fi

JAVA_PATH=$(which java)
if [[ ! -x "$JAVA_PATH" ]]; then
  echo "Error: Java executable not found. Please edit $0 to specify JAVA_PATH"
  exit 1
fi

if [[ ! -x $(which curl) ]]; then
  echo "Error: missing tool 'curl'. Please install it first."
  [[ -z $IS_MAC ]] && echo "Try: \$sudo apt-get install curl"
  exit 1
fi

# If you know which exact build number you have, you can set it here
# in the form "123.456789". Otherwise leave it blank and the script
# will figure it out.
FROM=

if [[ -z "$FROM" ]]; then
  FROM=$(head -n 1 build.txt | tr -d "AI-")
  echo "Current Studio: version $FROM"
  echo
fi

# If you know which exact build number you want to update to, you can
# set it here in the form "123.456789". Otherwise leave it blank and
# the script will figure it out.
TO=

if [[ -z "$TO" ]]; then
  XML_URL=https://dl.google.com/android/studio/patches/updates.xml
  TMP_XML=$(mktemp -t tmp.XXXXXX)
  echo "Download $XML_URL..."
  curl --fail --silent --show-error --output "$TMP_XML" "$XML_URL" || exit

  # find first build number in updates.xml
  TO=$(sed -n -e '/<build /s/.*number=\"\([^"]*\).*/\1/p' $TMP_XML | head -n 1)
  rm "$TMP_XML"
  echo "Availabe update: version $TO"
  echo
fi

if [[ "$TO" == "$FROM" ]]; then
  echo "You already have the latest Android Studio version $FROM."
  echo "There is nothing to update."
  echo
  exit
fi

echo "This script will download the Studio updater from $FROM to $TO."
echo
read -p "Press enter to start download..."

OS="unix"
if [[ -n "$IS_MAC" ]]; then OS="mac"; fi
JAR_URL="https://dl.google.com/android/studio/patches/AI-$FROM-$TO-patch-$OS.jar"
TMP_JAR=$(mktemp -d -t tmp.XXXXXX)/"AI-$FROM-$TO-patch-$OS.jar"
curl --fail --show-error --output "$TMP_JAR" "$JAR_URL" || exit

echo "Current Studio: version $FROM"
echo "Availabe update: version $TO"
echo
echo "Starting update: '$JAVA_PATH' -classpath '$TMP_JAR' com.intellij.updater.Runner install '$STUDIO_DIR'"

"$JAVA_PATH" -classpath "$TMP_JAR" com.intellij.updater.Runner install "$STUDIO_DIR"

read -p "Update finished. Press enter to terminate."
rm "$TMP_JAR"
exit 0

