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

if [ -z "$(command -v uname)" ] || [ -z "$(command -v realpath)" ] || [ -z "$(command -v dirname)" ] || [ -z "$(command -v cat)" ] || \
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
  echo "No JRE found. Please make sure \$STUDIO_JDK, \$JDK_HOME, or \$JAVA_HOME point to valid JRE installation."
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

STUDIO_VERSION=$(cat ../Resources/product-info.json | grep AndroidStudio | awk '{ print $2}' | sed 's/"//' | sed 's/"//' | sed 's/,//')
STUDIO_CONFIG_DIR=${CONFIG_HOME}/Google/${STUDIO_VERSION}
if [ ! -d "${STUDIO_CONFIG_DIR}" ]; then
  # Android Studio config is not set up
  echo "Android Studio config doesn't exist ${STUDIO_CONFIG_DIR} "
  exec "$IDE_BIN_HOME/../MacOS/studio" disableNonBundledPlugins dontReopenProjects
  exit
fi

STUDIO_SAFE_CONFIG_DIR=${CONFIG_HOME}/Google/${STUDIO_VERSION}.safe
if [ ! -d ${STUDIO_SAFE_CONFIG_DIR} ]; then
  mkdir ${STUDIO_SAFE_CONFIG_DIR}
fi

cp -R "${STUDIO_CONFIG_DIR}" "${STUDIO_SAFE_CONFIG_DIR}"
rm -rf "${STUDIO_SAFE_CONFIG_DIR}/idea.properties"
rm -rf "${STUDIO_SAFE_CONFIG_DIR}/studio.vmoptions"

CLASS_PATH="$IDE_HOME/lib/util.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/jdom.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/log4j.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/tools.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/app.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/3rd-party-rt.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/jna.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/platform-statistics-devkit.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/jps-model.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/rd-core.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/rd-framework.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/stats.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/protobuf.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/external-system-rt.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/forms_rt.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/intellij-test-discovery.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/rd-swing.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/annotations.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/groovy.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/annotations-java5.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/asm-9.2.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/asm-analysis-9.2.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/asm-commons-9.2.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/asm-tree-9.2.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/asm-util-9.2.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/byte-buddy-agent.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/error-prone-annotations.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/externalProcess-rt.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/grpc-netty-shaded.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/idea_rt.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/intellij-coverage-agent-1.0.673.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/jffi-1.3.9-native.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/jffi-1.3.9.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/jnr-a64asm-1.0.0.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/jnr-ffi-2.2.12.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/jnr-x86asm-1.0.2.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/junit.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/junit4.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/lz4-java.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/platform-objectSerializer-annotations.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/pty4j.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/rd-text.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/resources.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/util_rt.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/winp.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/ant/lib/ant.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/ant/lib/dbus-java-3.2.1.jar"
CLASS_PATH="$CLASS_PATH:$IDE_HOME/lib/ant/lib/java-utils-1.0.6.jar"

# ---------------------------------------------------------------------
# Run the IDE.
# ---------------------------------------------------------------------
IFS="$(printf '\n\t')"
# shellcheck disable=SC2086
exec "$JAVA_BIN" \
  -classpath "$CLASS_PATH" \
  ${VM_OPTIONS} \
  "-XX:ErrorFile=$HOME/java_error_in_studio_%p.log" \
  "-XX:HeapDumpPath=$HOME/java_error_in_studio_.hprof" \
  "-Djb.vmOptionsFile=${USER_VM_OPTIONS_FILE:-${VM_OPTIONS_FILE}}" \
  ${IDE_PROPERTIES_PROPERTY} \
  -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader -Didea.strict.classpath=true -Didea.vendor.name=Google -Didea.paths.selector="${STUDIO_VERSION}.safe" -Didea.platform.prefix=AndroidStudio -Didea.jre.check=true -Dsplash=true --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/java.nio.charset=ALL-UNNAMED --add-opens=java.base/java.text=ALL-UNNAMED --add-opens=java.base/java.time=ALL-UNNAMED --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED --add-opens=java.base/jdk.internal.vm=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED --add-opens=java.base/sun.security.ssl=ALL-UNNAMED --add-opens=java.base/sun.security.util=ALL-UNNAMED --add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED --add-opens=java.desktop/com.apple.eawt.event=ALL-UNNAMED --add-opens=java.desktop/com.apple.laf=ALL-UNNAMED --add-opens=java.desktop/java.awt=ALL-UNNAMED --add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED --add-opens=java.desktop/java.awt.event=ALL-UNNAMED --add-opens=java.desktop/java.awt.image=ALL-UNNAMED --add-opens=java.desktop/java.awt.peer=ALL-UNNAMED --add-opens=java.desktop/javax.swing=ALL-UNNAMED --add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED --add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED --add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED --add-opens=java.desktop/sun.awt.image=ALL-UNNAMED --add-opens=java.desktop/sun.awt=ALL-UNNAMED --add-opens=java.desktop/sun.font=ALL-UNNAMED --add-opens=java.desktop/sun.java2d=ALL-UNNAMED --add-opens=java.desktop/sun.lwawt=ALL-UNNAMED --add-opens=java.desktop/sun.lwawt.macosx=ALL-UNNAMED --add-opens=java.desktop/sun.swing=ALL-UNNAMED --add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED --add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED --add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED \
  com.intellij.idea.Main \
  "$@" disableNonBundledPlugins dontReopenProjects

