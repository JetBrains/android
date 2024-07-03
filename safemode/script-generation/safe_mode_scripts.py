mac_script = [
"""
#!/bin/sh
#
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# ---------------------------------------------------------------------
# Android Safe Mode startup script.
# ---------------------------------------------------------------------

if [ -z "$(command -v uname)" ] || [ -z "$(command -v realpath)" ] || [ -z "$(command -v dirname)" ] || [ -z "$(command -v cat)" ] || \\
   [ -z "$(command -v grep)" ]; then
  TOOLS_MSG="Required tools are missing:"
  for tool in uname realpath grep dirname cat ; do
     test -z "$(command -v $tool)" && TOOLS_MSG="$TOOLS_MSG $tool"
  done
  echo "$TOOLS_MSG (SHELL=$SHELL PATH=$PATH)"
  exit 1
fi

# shellcheck disable=SC2034
GREP_OPTIONS=''
OS_TYPE=$(uname -s)
OS_ARCH=$(uname -m)

# ---------------------------------------------------------------------
# Ensure $IDE_HOME points to the directory where the IDE is installed.
# ---------------------------------------------------------------------
IDE_BIN_HOME=$(dirname "$(realpath "$0")")
IDE_HOME=$(dirname "${IDE_BIN_HOME}")
CONFIG_HOME="${XDG_CONFIG_HOME:-${HOME}/Library/Application Support}"
APP_PACKAGE=$(dirname "${IDE_HOME/..}")

# ---------------------------------------------------------------------
# Locate a JRE installation directory command -v will be used to run the IDE.
# Try (in order): $STUDIO_JDK, .../studio.jdk, .../jbr, $JDK_HOME, $JAVA_HOME, "java" in $PATH.
# ---------------------------------------------------------------------
JRE="$IDE_HOME/jbr/Contents/Home"
JAVA_HOME=""
STUDIO_JDK=""
JDK_HOME=""
JAVA_BIN="$JRE/bin/java"

if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  echo "No JRE found. Please make sure \\$STUDIO_JDK, \\$JDK_HOME, or \\$JAVA_HOME point to valid JRE installation."
  exit 1
fi

# ---------------------------------------------------------------------
# Collect JVM options and IDE properties.
# ---------------------------------------------------------------------
IDE_PROPERTIES_PROPERTY=""
# shellcheck disable=SC2154
if [ -n "$STUDIO_PROPERTIES" ]; then
  IDE_PROPERTIES_PROPERTY="-Didea.properties.file=$STUDIO_PROPERTIES"
fi

VM_OPTIONS_FILE=""
USER_VM_OPTIONS_FILE=""

STUDIO_VERSION=$(cat "${IDE_HOME}/Resources/product-info.json" | grep AndroidStudio | awk '{ print $2}' | sed 's/"//' | sed 's/"//' | sed 's/,//')
STUDIO_CONFIG_DIR="${CONFIG_HOME}/Google/${STUDIO_VERSION}"
if [ ! -d "${STUDIO_CONFIG_DIR}" ]; then
  # Android Studio config is not set up
  echo "Android Studio config doesn't exist ${STUDIO_CONFIG_DIR} "
  exec "$IDE_BIN_HOME/../MacOS/studio" disableNonBundledPlugins dontReopenProjects
  exit
fi

STUDIO_SAFE_CONFIG_DIR="${STUDIO_CONFIG_DIR}.safe"
if [ ! -d "${STUDIO_SAFE_CONFIG_DIR}" ]; then
  mkdir "${STUDIO_SAFE_CONFIG_DIR}"
fi

cp -R "${STUDIO_CONFIG_DIR}" "${STUDIO_SAFE_CONFIG_DIR}"
rm -rf "${STUDIO_SAFE_CONFIG_DIR}/idea.properties"
rm -rf "${STUDIO_SAFE_CONFIG_DIR}/studio.vmoptions"

""",
"""

# ---------------------------------------------------------------------
# Run the IDE.
# ---------------------------------------------------------------------
IFS="$(printf '\\n\\t')"
# shellcheck disable=SC2086
exec "$JAVA_BIN" \\
  -classpath "$CLASS_PATH" \\
  ${VM_OPTIONS} \\
  "-XX:ErrorFile=$HOME/java_error_in_studio_%p.log" \\
  "-XX:HeapDumpPath=$HOME/java_error_in_studio_.hprof" \\
  "-Djb.vmOptionsFile=${USER_VM_OPTIONS_FILE:-${VM_OPTIONS_FILE}}" \\
  ${IDE_PROPERTIES_PROPERTY} \\
""",
"""
  com.intellij.idea.Main \\
  "$@" disableNonBundledPlugins dontReopenProjects
""",
]

lin_script = [
"""
#!/bin/sh
#
# Copyright (C) 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# ---------------------------------------------------------------------
# Android Studio startup script.
# ---------------------------------------------------------------------

message()
{
  TITLE="Cannot start Android Studio"
  if [ -n "$(command -v zenity)" ]; then
    zenity --error --title="$TITLE" --text="$1" --no-wrap
  elif [ -n "$(command -v kdialog)" ]; then
    kdialog --error "$1" --title "$TITLE"
  elif [ -n "$(command -v notify-send)" ]; then
    notify-send "ERROR: $TITLE" "$1"
  elif [ -n "$(command -v xmessage)" ]; then
    xmessage -center "ERROR: $TITLE: $1"
  else
    printf "ERROR: %s\\n%s\\n" "$TITLE" "$1"
  fi
}

if [ -z "$(command -v uname)" ] || [ -z "$(command -v realpath)" ] || [ -z "$(command -v dirname)" ] || [ -z "$(command -v cat)" ] || \\
   [ -z "$(command -v egrep)" ]; then
  TOOLS_MSG="Required tools are missing:"
  for tool in uname realpath egrep dirname cat ; do
     test -z "$(command -v $tool)" && TOOLS_MSG="$TOOLS_MSG $tool"
  done
  message "$TOOLS_MSG (SHELL=$SHELL PATH=$PATH)"
  exit 1
fi

# shellcheck disable=SC2034
GREP_OPTIONS=''
OS_TYPE=$(uname -s)
OS_ARCH=$(uname -m)

# ---------------------------------------------------------------------
# Ensure $IDE_HOME points to the directory where the IDE is installed.
# ---------------------------------------------------------------------
IDE_BIN_HOME=$(dirname "$(realpath "$0")")
IDE_HOME=$(dirname "${IDE_BIN_HOME}")
CONFIG_HOME="${XDG_CONFIG_HOME:-${HOME}/.config}"

# ---------------------------------------------------------------------
# Locate a JRE installation directory command -v will be used to run the IDE.
# Try (in order): $STUDIO_JDK, .../studio.jdk, .../jbr, $JDK_HOME, $JAVA_HOME, "java" in $PATH.
# ---------------------------------------------------------------------
JRE="$IDE_HOME/jbr"
JAVA_HOME=""
STUDIO_JDK=""
JDK_HOME=""
JAVA_BIN="$JRE/bin/java"
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  message "ERROR: cannot start Android Studio.\\nNo JRE found. Please make sure \\$STUDIO_JDK, \\$JDK_HOME, or \\$JAVA_HOME point to valid JRE installation."
  exit 1
fi

# Collect JVM options and IDE properties.
# ---------------------------------------------------------------------
IDE_PROPERTIES_PROPERTY=""
# shellcheck disable=SC2154
if [ -n "$STUDIO_PROPERTIES" ]; then
  IDE_PROPERTIES_PROPERTY="-Didea.properties.file=$STUDIO_PROPERTIES"
fi

VM_OPTIONS_FILE="${IDE_BIN_HOME}/studio64.vmoptions"
VM_OPTIONS_FILE=""
USER_VM_OPTIONS_FILE=""

STUDIO_BIN="${IDE_BIN_HOME}/studio.sh"
STUDIO_VERSION=$(cat "${IDE_HOME}/product-info.json" | grep AndroidStudio | awk '{ print $2 }' | sed 's/"//' | sed 's/"//' | sed 's/,//')
STUDIO_CONFIG_DIR=${CONFIG_HOME}/Google/${STUDIO_VERSION}
if [ ! -d "${STUDIO_CONFIG_DIR}" ]; then
  # Android Studio config is not set up
  echo "Android Studio config doesn't exist ${STUDIO_CONFIG_DIR} "
  ${STUDIO_BIN} disableNonBundledPlugins dontReopenProjects
  exit
fi

STUDIO_VERSION_SAFE="${STUDIO_VERSION}.safe"
STUDIO_SAFE_CONFIG_DIR=${CONFIG_HOME}/Google/${STUDIO_VERSION_SAFE}
if [ ! -d “${STUDIO_SAFE_CONFIG_DIR}” ]; then
  mkdir “${STUDIO_SAFE_CONFIG_DIR}”
fi

cp -R "${STUDIO_CONFIG_DIR}" "${STUDIO_SAFE_CONFIG_DIR}"
rm -rf ${STUDIO_SAFE_CONFIG_DIR}/idea.properties
rm -rf ${STUDIO_SAFE_CONFIG_DIR}/studio64.vmoptions

""",
"""

# ---------------------------------------------------------------------
# Run the IDE.
# ---------------------------------------------------------------------
# shellcheck disable=SC2086
exec "$JAVA_BIN" \\
  -classpath "$CLASS_PATH" \\
  ${VM_OPTIONS} \\
  "-XX:ErrorFile=$HOME/java_error_in_studio_%p.log" \\
  "-XX:HeapDumpPath=$HOME/java_error_in_studio_.hprof" \\
  "-Djb.vmOptionsFile=${USER_VM_OPTIONS_FILE:-${VM_OPTIONS_FILE}}" \\
  ${IDE_PROPERTIES_PROPERTY} \\
""",
"""
  com.intellij.idea.Main \\
  "$@" disableNonBundledPlugins dontReopenProjects
""",
]

win_script = [
"""
@ECHO OFF
SETLOCAL

::----------------------------------------------------------------------
:: Android Studio startup script.
::----------------------------------------------------------------------

:: ---------------------------------------------------------------------
:: Ensure System32 is in PATH.
:: ---------------------------------------------------------------------
SET PATH=%PATH%;%systemroot%\\system32

:: ---------------------------------------------------------------------
:: Ensure IDE_HOME points to the directory where the IDE is installed.
:: ---------------------------------------------------------------------
SET IDE_BIN_DIR=%~dp0
FOR /F "delims=" %%i in ("%IDE_BIN_DIR%\\..") DO SET IDE_HOME=%%~fi

:: ---------------------------------------------------------------------
:: Locate a JRE installation directory which will be used to run the IDE.
:: Try (in order): STUDIO_JDK, studio64.exe.jdk, ..\\jbr[-x86], JDK_HOME, JAVA_HOME.
:: ---------------------------------------------------------------------
SET JRE=%IDE_HOME%\\jbr
SET JAVA_HOME=
SET STUDIO_JDK=
SET JDK_HOME=
SET JAVA_EXE=%JRE%\\bin\\java.exe
IF NOT EXIST "%JAVA_EXE%" (
  ECHO ERROR: cannot start Android Studio.
  ECHO No JRE found. Please make sure STUDIO_JDK, JDK_HOME, or JAVA_HOME point to a valid JRE installation.
  EXIT /B
)

:: ---------------------------------------------------------------------
:: Collect JVM options and properties.
:: ---------------------------------------------------------------------
IF NOT "%STUDIO_PROPERTIES%" == "" SET IDE_PROPERTIES_PROPERTY="-Didea.properties.file=%STUDIO_PROPERTIES%"

SET VM_OPTIONS_FILE=
SET USER_VM_OPTIONS_FILE=

FOR /F "tokens=2 delims=: " %%x in ('FINDSTR AndroidStudio "%IDE_HOME%\\product-info.json"') do SET STUDIO_VERSION=%%x
SET STUDIO_VERSION=%STUDIO_VERSION: =%
SET STUDIO_VERSION=%STUDIO_VERSION:"=%
SET STUDIO_VERSION=%STUDIO_VERSION:,=%

SET STUDIO_CONFIG_DIR="%APPDATA%\\Google\\%STUDIO_VERSION%\\"
IF NOT EXIST %STUDIO_CONFIG_DIR% (
  @REM Android Studio config is not set up
  ECHO Android Studio config doesn't exist %STUDIO_CONFIG_DIR%
  "%IDE_HOME%\\bin\\studio64.exe" disableNonBundledPlugins dontReopenProjects
  EXIT /B
)

SET STUDIO_SAFE_CONFIG_PATH=%APPDATA%\\Google\\%STUDIO_VERSION%.safe\\
SET STUDIO_SAFE_CONFIG_DIR="%STUDIO_SAFE_CONFIG_PATH%"
IF NOT EXIST %STUDIO_SAFE_CONFIG_DIR% (
  mkdir %STUDIO_SAFE_CONFIG_DIR%
)

xcopy /YS %STUDIO_CONFIG_DIR%* %STUDIO_SAFE_CONFIG_DIR%
del %STUDIO_SAFE_CONFIG_DIR%idea.properties
del %STUDIO_SAFE_CONFIG_DIR%studio64.exe.vmoptions

SET VM_OPTIONS_FILE="%IDE_HOME%\\bin\\studio64.exe.vmoptions"
SET ACC="-Djb.vmOptionsFile=%VM_OPTIONS_FILE%"

""",
"""

:: ---------------------------------------------------------------------
:: Run the IDE.
:: ---------------------------------------------------------------------
"%JAVA_EXE%" ^
  -cp "%CLASS_PATH%" ^
  %ACC% ^
  "-XX:ErrorFile=%USERPROFILE%\\java_error_in_studio_%%p.log" ^
  "-XX:HeapDumpPath=%USERPROFILE%\\java_error_in_studio.hprof" ^
  %IDE_PROPERTIES_PROPERTY% ^
""",
"""
  com.intellij.idea.Main ^
  %* disableNonBundledPlugins dontReopenProjects
""",
]
