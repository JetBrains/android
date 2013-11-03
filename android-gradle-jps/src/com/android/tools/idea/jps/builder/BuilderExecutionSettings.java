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
import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.GlobalOptions;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Settings used to build a Gradle project.
 */
class BuilderExecutionSettings {
  private final boolean myEmbeddedGradleDaemonEnabled;
  private final int myGradleDaemonMaxIdleTimeInMs;

  @NotNull private final List<String> myGradleDaemonJvmOptions = Lists.newArrayList();
  @NotNull private final Set<String> myModulesToBuildNames = Sets.newHashSet();

  @Nullable private final File myGradleHomeDir;
  @Nullable private final File myGradleServiceDir;
  @Nullable private final File myJavaHomeDir;
  @NotNull private final File myProjectDir;
  @NotNull private final BuildMode myBuildMode;
  private final boolean myVerboseLoggingEnabled;
  private final boolean myParallelBuild;

  BuilderExecutionSettings() {
    myEmbeddedGradleDaemonEnabled = SystemProperties.getBooleanProperty(BuildProcessJvmArgs.USE_EMBEDDED_GRADLE_DAEMON, false);
    myGradleDaemonMaxIdleTimeInMs = SystemProperties.getIntProperty(BuildProcessJvmArgs.GRADLE_DAEMON_MAX_IDLE_TIME_IN_MS, -1);
    myGradleHomeDir = findDir(BuildProcessJvmArgs.GRADLE_HOME_DIR_PATH, "Gradle home");
    myGradleServiceDir = findDir(BuildProcessJvmArgs.GRADLE_SERVICE_DIR_PATH, "Gradle service");
    myJavaHomeDir = findDir(BuildProcessJvmArgs.GRADLE_JAVA_HOME_DIR_PATH, "Java home");
    myProjectDir = findProjectRootDir();
    String buildActionName = System.getProperty(BuildProcessJvmArgs.BUILD_ACTION);
    myBuildMode = Strings.isNullOrEmpty(buildActionName) ? BuildMode.DEFAULT_BUILD_MODE : BuildMode.valueOf(buildActionName);
    myVerboseLoggingEnabled = SystemProperties.getBooleanProperty(BuildProcessJvmArgs.USE_GRADLE_VERBOSE_LOGGING, false);
    myParallelBuild = SystemProperties.getBooleanProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, false);
    populateModulesToBuild();
    populateGradleDaemonJvmOptions();
    populateHttpProxyJvmOptions();
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

  private void populateModulesToBuild() {
    int moduleCount = SystemProperties.getIntProperty(BuildProcessJvmArgs.MODULES_TO_BUILD_PROPERTY_COUNT, 0);
    for (int i = 0; i < moduleCount; i++) {
      String module = System.getProperty(BuildProcessJvmArgs.MODULES_TO_BUILD_PROPERTY_PREFIX + i);
      if (!Strings.isNullOrEmpty(module)) {
        myModulesToBuildNames.add(module);
      }
    }
  }

  private void populateGradleDaemonJvmOptions() {
    int vmOptionCount = SystemProperties.getIntProperty(BuildProcessJvmArgs.GRADLE_DAEMON_JVM_OPTION_COUNT, 0);
    for (int i = 0; i < vmOptionCount; i++) {
      String jvmOption = System.getProperty(BuildProcessJvmArgs.GRADLE_DAEMON_JVM_OPTION_PREFIX + i);
      if (!Strings.isNullOrEmpty(jvmOption)) {
        myGradleDaemonJvmOptions.add(jvmOption);
      }
    }
  }

  private void populateHttpProxyJvmOptions() {
    int vmOptionCount = SystemProperties.getIntProperty(BuildProcessJvmArgs.HTTP_PROXY_PROPERTY_COUNT, 0);
    for (int i = 0; i < vmOptionCount; i++) {
      String jvmOption = System.getProperty(BuildProcessJvmArgs.HTTP_PROXY_PROPERTY_PREFIX + i);
      if (!Strings.isNullOrEmpty(jvmOption)) {
        int indexOfSeparator = jvmOption.indexOf(BuildProcessJvmArgs.HTTP_PROXY_PROPERTY_SEPARATOR);
        if (indexOfSeparator < 0 || indexOfSeparator >= jvmOption.length() -1) {
          continue;
        }
        String name = jvmOption.substring(0, indexOfSeparator);
        String value = jvmOption.substring(indexOfSeparator + 1);
        myGradleDaemonJvmOptions.add(AndroidGradleSettings.createJvmArg(name, value));
      }
    }
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
  List<String> getGradleDaemonJvmOptions() {
    return myGradleDaemonJvmOptions;
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

  @NotNull
  BuildMode getBuildMode() {
    return myBuildMode;
  }

  boolean isParallelBuild() {
    return myParallelBuild;
  }

  @NotNull
  Set<String> getModulesToBuildNames() {
    return myModulesToBuildNames;
  }

  @Override
  public String toString() {
    return "BuilderExecutionSettings[" +
           "embeddedGradleDaemonEnabled=" + myEmbeddedGradleDaemonEnabled +
           ", buildMode=" + myBuildMode.name() +
           ", gradleDaemonMaxIdleTimeInMs=" + myGradleDaemonMaxIdleTimeInMs +
           ", gradleDaemonJvmOptions=" + myGradleDaemonJvmOptions +
           ", gradleHomeDir=" + myGradleHomeDir +
           ", gradleServiceDir=" + myGradleServiceDir +
           ", javaHomeDir=" + myJavaHomeDir +
           ", parallelBuild=" + myParallelBuild +
           ", projectDir=" + myProjectDir +
           ", verboseLoggingEnabled=" + myVerboseLoggingEnabled +
           ", modulesToBuildNames=" + myModulesToBuildNames +
           ']';
  }
}
