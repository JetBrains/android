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
public class BuildProcessJvmArgs {
  @NonNls public static final String GRADLE_DAEMON_MAX_IDLE_TIME_IN_MS = "com.android.studio.gradle.daemon.max.idle.time";
  @NonNls public static final String GRADLE_DAEMON_VM_OPTION_COUNT = "com.android.studio.daemon.gradle.vm.option.count";
  @NonNls public static final String GRADLE_DAEMON_VM_OPTION_DOT = "com.android.studio.daemon.gradle.vm.option.";
  @NonNls public static final String GRADLE_JAVA_HOME_DIR_PATH = "com.android.studio.gradle.java.home.path";
  @NonNls public static final String GRADLE_HOME_DIR_PATH = "com.android.studio.gradle.home.path";
  @NonNls public static final String GRADLE_SERVICE_DIR_PATH = "com.android.studio.gradle.service.dir.path";
  @NonNls public static final String PROJECT_DIR_PATH = "com.android.studio.gradle.project.path";
  @NonNls public static final String USE_EMBEDDED_GRADLE_DAEMON = "com.android.studio.gradle.use.embedded.daemon";
  @NonNls public static final String USE_GRADLE_VERBOSE_LOGGING = "com.android.studio.gradle.use.verbose.logging";
  @NonNls public static final String GENERATE_SOURCE_ONLY_ON_COMPILE = "com.android.studio.gradle.generate.source.only.on.compile";
}
