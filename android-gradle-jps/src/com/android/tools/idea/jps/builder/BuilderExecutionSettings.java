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

import com.android.tools.idea.gradle.util.AndroidGradleSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.GlobalOptions;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.gradle.compiler.BuildProcessJvmArgs.*;

/**
 * Settings used to build a Gradle project.
 */
class BuilderExecutionSettings {
  @Nullable private final File myGradleHomeDir;
  @Nullable private final File myGradleServiceDir;
  @Nullable private final File myJavaHomeDir;

  @NotNull private final File myProjectDir;
  @NotNull private final BuildMode myBuildMode;
  @NotNull private final List<String> myGradleTasksToInvoke;
  @NotNull private final List<String> myCommandLineOptions;
  @NotNull private final List<String> myJvmOptions;

  private final boolean myEmbeddedModeEnabled;
  private final boolean myVerboseLoggingEnabled;
  private final boolean myParallelBuild;
  private final boolean myOfflineBuildMode;
  private final boolean myConfigureOnDemand;

  BuilderExecutionSettings() {
    myEmbeddedModeEnabled = SystemProperties.getBooleanProperty(USE_EMBEDDED_GRADLE_DAEMON, false);
    myGradleHomeDir = findDir(GRADLE_HOME_DIR_PATH, "Gradle home");
    myGradleServiceDir = findDir(GRADLE_SERVICE_DIR_PATH, "Gradle service");
    myJavaHomeDir = findDir(GRADLE_JAVA_HOME_DIR_PATH, "Java home");
    myProjectDir = findProjectRootDir();
    String buildActionName = System.getProperty(BUILD_MODE);
    myBuildMode = Strings.isNullOrEmpty(buildActionName) ? BuildMode.DEFAULT_BUILD_MODE : BuildMode.valueOf(buildActionName);
    myGradleTasksToInvoke = getJvmArgGroup(GRADLE_TASKS_TO_INVOKE_PROPERTY_PREFIX);
    myCommandLineOptions = getJvmArgGroup(GRADLE_DAEMON_COMMAND_LINE_OPTION_PREFIX);
    myJvmOptions = getJvmArgGroup(GRADLE_DAEMON_JVM_OPTION_PREFIX);
    myVerboseLoggingEnabled = SystemProperties.getBooleanProperty(USE_GRADLE_VERBOSE_LOGGING, false);
    myParallelBuild = SystemProperties.getBooleanProperty(GlobalOptions.COMPILE_PARALLEL_OPTION, false);
    myOfflineBuildMode = SystemProperties.getBooleanProperty(GRADLE_OFFLINE_BUILD_MODE, false);
    myConfigureOnDemand = SystemProperties.getBooleanProperty(GRADLE_CONFIGURATION_ON_DEMAND, false);
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
    File projectRootDir = createFile(PROJECT_DIR_PATH);
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

  @NotNull
  private static List<String> getJvmArgGroup(@NotNull String argPrefix) {
    List<String> args = Lists.newArrayList();
    int counter = 0;
    while (true) {
      String arg = System.getProperty(argPrefix + counter++);
      if (Strings.isNullOrEmpty(arg)) {
        break;
      }
      args.add(arg);
    }
    return args;
  }

  private void populateHttpProxyJvmOptions() {
    int counter = 0;
    while (true) {
      String jvmOption = System.getProperty(HTTP_PROXY_PROPERTY_PREFIX + counter++);
      if (Strings.isNullOrEmpty(jvmOption)) {
        break;
      }
      int indexOfSeparator = jvmOption.indexOf(HTTP_PROXY_PROPERTY_SEPARATOR);
      if (indexOfSeparator < 0 || indexOfSeparator >= jvmOption.length() - 1) {
        continue;
      }
      String arg =
        AndroidGradleSettings.createJvmArg(jvmOption.substring(0, indexOfSeparator), jvmOption.substring(indexOfSeparator + 1));
      myJvmOptions.add(arg);
    }
  }

  boolean isEmbeddedModeEnabled() {
    return myEmbeddedModeEnabled;
  }

  boolean isVerboseLoggingEnabled() {
    return myVerboseLoggingEnabled;
  }

  @NotNull
  List<String> getCommandLineOptions() {
    return myCommandLineOptions;
  }

  @NotNull
  List<String> getJvmOptions() {
    return myJvmOptions;
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

  boolean isOfflineBuild() {
    return myOfflineBuildMode;
  }

  public boolean isConfigureOnDemand() {
    return myConfigureOnDemand;
  }

  @NotNull
  List<String> getGradleTasksToInvoke() {
    return myGradleTasksToInvoke;
  }

  @Override
  public String toString() {
    return "BuilderExecutionSettings[" +
           "buildMode=" + myBuildMode.name() +
           ", commandLineOptions=" + myCommandLineOptions +
           ", embeddedModeEnabled=" + myEmbeddedModeEnabled +
           ", gradleHomeDir=" + myGradleHomeDir +
           ", gradleServiceDir=" + myGradleServiceDir +
           ", javaHomeDir=" + myJavaHomeDir +
           ", jvmOptions=" + myJvmOptions +
           ", gradleTasksToInvoke=" + myGradleTasksToInvoke +
           ", offlineBuild=" + myOfflineBuildMode +
           ", parallelBuild=" + myParallelBuild +
           ", projectDir=" + myProjectDir +
           ", verboseLoggingEnabled=" + myVerboseLoggingEnabled +
           ']';
  }
}
