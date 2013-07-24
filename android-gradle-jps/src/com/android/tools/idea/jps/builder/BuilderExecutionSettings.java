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
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.GlobalOptions;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Settings used to build a Gradle project.
 */
class BuilderExecutionSettings {
  private final boolean myEmbeddedGradleDaemonEnabled;
  private final int myGradleDaemonMaxIdleTimeInMs;
  @NotNull private final List<String> myGradleDaemonVmOptions;
  @Nullable private final File myGradleHomeDir;
  @Nullable private final File myGradleServiceDir;
  @Nullable private final File myJavaHomeDir;
  @NotNull private final File myProjectDir;
  private final boolean myVerboseLoggingEnabled;
  private final boolean myParallelBuild;
  private final boolean myGenerateSourceOnly;

  BuilderExecutionSettings() {
    myEmbeddedGradleDaemonEnabled = SystemProperties.getBooleanProperty(BuildProcessJvmArgs.USE_EMBEDDED_GRADLE_DAEMON, false);
    myGradleDaemonMaxIdleTimeInMs = SystemProperties.getIntProperty(BuildProcessJvmArgs.GRADLE_DAEMON_MAX_IDLE_TIME_IN_MS, -1);
    myGradleDaemonVmOptions = getJvmOptions();
    myGradleHomeDir = findDir(BuildProcessJvmArgs.GRADLE_HOME_DIR_PATH, "Gradle home");
    myGradleServiceDir = findDir(BuildProcessJvmArgs.GRADLE_SERVICE_DIR_PATH, "Gradle service");
    myJavaHomeDir = findDir(BuildProcessJvmArgs.GRADLE_JAVA_HOME_DIR_PATH, "Java home");
    myProjectDir = findProjectRootDir();
    myVerboseLoggingEnabled = SystemProperties.getBooleanProperty(BuildProcessJvmArgs.USE_GRADLE_VERBOSE_LOGGING, false);
    myParallelBuild = SystemProperties.getBooleanProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, false);
    myGenerateSourceOnly = SystemProperties.getBooleanProperty(BuildProcessJvmArgs.GENERATE_SOURCE_ONLY_ON_COMPILE, false);
  }

  @NotNull
  private static List<String> getJvmOptions() {
    int vmOptionCount = SystemProperties.getIntProperty(BuildProcessJvmArgs.GRADLE_DAEMON_VM_OPTION_COUNT, 0);
    if (vmOptionCount <= 0) {
      return Collections.emptyList();
    }
    List<String> vmOptions = Lists.newArrayList();
    for (int i = 0; i < vmOptionCount; i++) {
      String vmOption = System.getProperty(BuildProcessJvmArgs.GRADLE_DAEMON_VM_OPTION_DOT + i);
      if (!Strings.isNullOrEmpty(vmOption)) {
        vmOptions.add(vmOption);
      }
    }
    return vmOptions;
  }

  @Nullable
  private static File findDir(@NotNull String jvmArgName, @NotNull String dirType) {
    File gradleServiceDir = createFile(jvmArgName);
    if (gradleServiceDir == null) {
      return null;
    }
    ensureDirectoryExists(gradleServiceDir, dirType);
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
    return path != null && !path.isEmpty() ? new File(path) : null;
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

  @NotNull
  List<String> getGradleDaemonVmOptions() {
    return myGradleDaemonVmOptions;
  }

  @Nullable
  File getGradleHomeDir() {
    return myGradleHomeDir;
  }

  @Nullable
  File getGradleServiceDir() {
    return myGradleServiceDir;
  }

  @Nullable
  File getJavaHomeDir() {
    return myJavaHomeDir;
  }

  @NotNull
  File getProjectDir() {
    return myProjectDir;
  }

  boolean isGenerateSourceOnly() {
    return myGenerateSourceOnly;
  }

  boolean isParallelBuild() {
    return myParallelBuild;
  }

  @Override
  public String toString() {
    return "BuilderExecutionSettings[" +
           "embeddedGradleDaemonEnabled=" + myEmbeddedGradleDaemonEnabled +
           ", generateSourceOnly=" + myGenerateSourceOnly +
           ", gradleDaemonMaxIdleTimeInMs=" + myGradleDaemonMaxIdleTimeInMs +
           ", gradleDaemonVmOptions=" + myGradleDaemonVmOptions +
           ", gradleHomeDir=" + myGradleHomeDir +
           ", gradleServiceDir=" + myGradleServiceDir +
           ", javaHomeDir=" + myJavaHomeDir +
           ", parallelBuild=" + myParallelBuild +
           ", projectDir=" + myProjectDir +
           ", verboseLoggingEnabled=" + myVerboseLoggingEnabled +
           ']';
  }
}
