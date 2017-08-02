/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.compiler;

import org.jetbrains.annotations.NonNls;

/**
 * JVM arguments passed to the build process, when building a Gradle project.
 */
public final class BuildProcessJvmArgs {

  private BuildProcessJvmArgs() {
  }

  @NonNls private static final String JVM_ARG_PREFIX = "com.android.studio.gradle.";

  @NonNls public static final String GRADLE_DAEMON_JVM_OPTION_PREFIX = JVM_ARG_PREFIX +  "daemon.jvm.option.";
  @NonNls public static final String GRADLE_DAEMON_COMMAND_LINE_OPTION_PREFIX = JVM_ARG_PREFIX + "daemon.command.line.option.";
  @NonNls public static final String GRADLE_HOME_DIR_PATH = JVM_ARG_PREFIX + "home.path";
  @NonNls public static final String GRADLE_JAVA_HOME_DIR_PATH = JVM_ARG_PREFIX + "java.home.path";
  @NonNls public static final String GRADLE_OFFLINE_BUILD_MODE = JVM_ARG_PREFIX + "offline.mode";
  @NonNls public static final String GRADLE_CONFIGURATION_ON_DEMAND = JVM_ARG_PREFIX + "configuration.on.demand";
  @NonNls public static final String GRADLE_SERVICE_DIR_PATH = JVM_ARG_PREFIX + "service.dir.path";
  @NonNls public static final String PROJECT_DIR_PATH = JVM_ARG_PREFIX + "project.path";
  @NonNls public static final String USE_EMBEDDED_GRADLE_DAEMON = JVM_ARG_PREFIX + "use.embedded.daemon";
  @NonNls public static final String USE_GRADLE_VERBOSE_LOGGING = JVM_ARG_PREFIX + "use.verbose.logging";
  @NonNls public static final String BUILD_MODE = JVM_ARG_PREFIX + "build.mode";
  @NonNls public static final String HTTP_PROXY_PROPERTY_PREFIX = JVM_ARG_PREFIX + "proxy.property.";
  @NonNls public static final String HTTP_PROXY_PROPERTY_SEPARATOR = ":";
  @NonNls public static final String GRADLE_TASKS_TO_INVOKE_PROPERTY_PREFIX = JVM_ARG_PREFIX + "gradle.tasks.";
}
