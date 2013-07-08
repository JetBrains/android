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
package com.android.tools.idea.jps.builder;

import com.android.tools.idea.gradle.compiler.BuildProcessJvmArgs;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.GlobalOptions;

import java.io.File;

/**
 * Settings used to build a Gradle project.
 */
class BuilderExecutionSettings {
  private final boolean myEmbeddedGradleDaemonEnabled;
  private final int myGradleDaemonMaxIdleTimeInMs;
  private final int myGradleDaemonMaxMemoryInMb;
  @Nullable private final File myGradleHomeDir;
  @Nullable private final File myGradleServiceDir;
  @NotNull private final File myProjectDir;
  private final boolean myVerboseLoggingEnabled;
  private final boolean myParallelBuild;

  BuilderExecutionSettings() {
    myEmbeddedGradleDaemonEnabled = SystemProperties.getBooleanProperty(BuildProcessJvmArgs.USE_EMBEDDED_GRADLE_DAEMON, false);
    myGradleDaemonMaxIdleTimeInMs = SystemProperties.getIntProperty(BuildProcessJvmArgs.GRADLE_DAEMON_MAX_IDLE_TIME_IN_MS, -1);
    myGradleDaemonMaxMemoryInMb = SystemProperties.getIntProperty(BuildProcessJvmArgs.GRADLE_DAEMON_MAX_MEMORY_IN_MB, 512);
    myGradleHomeDir = findGradleHomeDir();
    myGradleServiceDir = findGradleServiceDir();
    myProjectDir = findProjectRootDir();
    myVerboseLoggingEnabled = SystemProperties.getBooleanProperty(BuildProcessJvmArgs.USE_GRADLE_VERBOSE_LOGGING, false);
    myParallelBuild = SystemProperties.getBooleanProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, false);
  }

  @Nullable
  private static File findGradleHomeDir() {
    File gradleHomeDir = createFile(BuildProcessJvmArgs.GRADLE_HOME_DIR_PATH);
    if (gradleHomeDir == null) {
      return null;
    }
    ensureDirectoryExists(gradleHomeDir, "Gradle home");
    return gradleHomeDir;
  }

  @Nullable
  private static File findGradleServiceDir() {
    File gradleServiceDir = createFile(BuildProcessJvmArgs.GRADLE_SERVICE_DIR_PATH);
    if (gradleServiceDir == null) {
      return null;
    }
    ensureDirectoryExists(gradleServiceDir, "Gradle service");
    return gradleServiceDir;
  }

  @NotNull
  private static File findProjectRootDir() {
    File projectRootDir = createFile(BuildProcessJvmArgs.PROJECT_DIR_PATH);
    if (projectRootDir == null) {
      throw new NullPointerException("Project directory not specified");
    }
    ensureDirectoryExists(projectRootDir, "project");
    return projectRootDir;
  }

  private static void ensureDirectoryExists(@NotNull File dir, @NotNull String type) {
    if (!dir.isDirectory()) {
      String path = dir.getPath();
      String msg = String.format("Unable to obtain %1$s directory: the file '%2$s' is not a directory", type, path);
      throw new IllegalArgumentException(msg);
    }
  }

  @Nullable
  private static File createFile(@NotNull String jvmArgName) {
    String path = System.getProperty(jvmArgName);
    if (path == null || path.isEmpty()) {
      return null;
    }
    if (path.startsWith("\"") && path.endsWith("\"")) {
      path = path.substring(1, path.length() - 1);
    }
    return new File(path);
  }

  boolean isEmbeddedGradleDaemonEnabled() {
    return myEmbeddedGradleDaemonEnabled;
  }

  boolean isVerboseLoggingEnabled() {
    return myVerboseLoggingEnabled;
  }

  int getGradleDaemonMaxIdleTimeInMs() {
    return myGradleDaemonMaxIdleTimeInMs;
  }

  int getGradleDaemonMaxMemoryInMb() {
    return myGradleDaemonMaxMemoryInMb;
  }

  @Nullable
  File getGradleHomeDir() {
    return myGradleHomeDir;
  }

  @Nullable
  File getGradleServiceDir() {
    return myGradleServiceDir;
  }

  @NotNull
  File getProjectDir() {
    return myProjectDir;
  }

  @Override
  public String toString() {
    return "BuilderExecutionSettings[" +
           "embeddedGradleDaemonEnabled=" + myEmbeddedGradleDaemonEnabled +
           ", gradleDaemonMaxIdleTimeInMs=" + myGradleDaemonMaxIdleTimeInMs +
           ", gradleDaemonMaxMemoryInMb=" + myGradleDaemonMaxMemoryInMb +
           ", gradleHomeDir=" + myGradleHomeDir +
           ", gradleServiceDir=" + myGradleServiceDir +
           ", parallelBuild=" + myParallelBuild +
           ", projectDir=" + myProjectDir +
           ", verboseLoggingEnabled=" + myVerboseLoggingEnabled +
           ']';
  }

  boolean isParallelBuild() {
    return myParallelBuild;
  }
}
