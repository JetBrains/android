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

import com.android.tools.idea.gradle.invoker.GradleInvoker;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.KeyValue;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.compiler.BuildProcessJvmArgs.*;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createJvmArg;
import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE;
import static com.android.tools.idea.gradle.util.BuildMode.ASSEMBLE_TRANSLATE;
import static com.android.tools.idea.gradle.util.GradleBuilds.ASSEMBLE_TRANSLATE_TASK_NAME;
import static com.android.tools.idea.gradle.util.GradleBuilds.DEFAULT_ASSEMBLE_TASK_NAME;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleExecutionSettings;
import static com.android.tools.idea.gradle.util.Projects.*;
import static com.android.tools.idea.startup.AndroidStudioInitializer.isAndroidStudio;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

/**
 * Adds extra Android-Gradle related configuration options.
 */
public class AndroidGradleBuildProcessParametersProvider extends BuildProcessParametersProvider {
  @NotNull private final Project myProject;

  public AndroidGradleBuildProcessParametersProvider(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public List<String> getVMArguments() {
    if (!isBuildWithGradle(myProject)) {
      return Collections.emptyList();
    }
    List<String> jvmArgs = Lists.newArrayList();

    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(myProject);
    populateJvmArgs(buildConfiguration, jvmArgs, myProject);

    GradleExecutionSettings executionSettings = getGradleExecutionSettings(myProject);
    if (executionSettings != null) {
      populateJvmArgs(executionSettings, jvmArgs);
    }

    populateJvmArgs(BuildSettings.getInstance(myProject), jvmArgs);

    if (!isAndroidStudio()) {
      // See https://code.google.com/p/android/issues/detail?id=169743
      addHttpProxySettings(jvmArgs);
    }
    return jvmArgs;
  }

  @VisibleForTesting
  static void populateJvmArgs(@NotNull AndroidGradleBuildConfiguration buildConfiguration,
                              @NotNull List<String> jvmArgs,
                              @NotNull Project project) {
    // Indicate whether build is in "offline" mode.
    jvmArgs.add(createJvmArg(GRADLE_OFFLINE_BUILD_MODE, GradleSettings.getInstance(project).isOfflineWork()));

    // Indicate whether "configuration on demand" is enabled.
    jvmArgs.add(createJvmArg(GRADLE_CONFIGURATION_ON_DEMAND, buildConfiguration.USE_CONFIGURATION_ON_DEMAND));

    // Add command-line options.
    String[] commandLineOptions = buildConfiguration.getCommandLineOptions();
    int optionCount = 0;
    for (String option : commandLineOptions) {
      String name = GRADLE_DAEMON_COMMAND_LINE_OPTION_PREFIX + optionCount++;
      jvmArgs.add(createJvmArg(name, option));
    }
  }

  @VisibleForTesting
  void populateJvmArgs(@NotNull GradleExecutionSettings executionSettings, @NotNull List<String> jvmArgs) {
    String gradleHome = executionSettings.getGradleHome();
    if (gradleHome != null && !gradleHome.isEmpty()) {
      gradleHome = toSystemDependentName(gradleHome);
      jvmArgs.add(createJvmArg(GRADLE_HOME_DIR_PATH, gradleHome));
    }

    String serviceDirectory = executionSettings.getServiceDirectory();
    if (serviceDirectory != null && !serviceDirectory.isEmpty()) {
      serviceDirectory = toSystemDependentName(serviceDirectory);
      jvmArgs.add(createJvmArg(GRADLE_SERVICE_DIR_PATH, serviceDirectory));
    }

    File javaHome = IdeSdks.getJdkPath();
    if (javaHome != null) {
      jvmArgs.add(createJvmArg(GRADLE_JAVA_HOME_DIR_PATH, javaHome.getPath()));
    }

    jvmArgs.add(createJvmArg(PROJECT_DIR_PATH, getBaseDirPath(myProject).getPath()));

    boolean verboseProcessing = executionSettings.isVerboseProcessing();
    jvmArgs.add(createJvmArg(USE_GRADLE_VERBOSE_LOGGING, verboseProcessing));

    if (!isAndroidStudio()) {
      // See https://code.google.com/p/android/issues/detail?id=169743
      String jvmOptions = executionSettings.getDaemonVmOptions();
      int jvmOptionCount = 0;
      if (jvmOptions != null && !jvmOptions.isEmpty()) {
        CommandLineTokenizer tokenizer = new CommandLineTokenizer(jvmOptions);
        while(tokenizer.hasMoreTokens()) {
          String name = GRADLE_DAEMON_JVM_OPTION_PREFIX + jvmOptionCount++;
          jvmArgs.add(createJvmArg(name, tokenizer.nextToken()));
        }
      }
    }
  }

  private static void addHttpProxySettings(@NotNull List<String> jvmArgs) {
    List<KeyValue<String, String>> proxyProperties = HttpConfigurable.getJvmPropertiesList(false, null);
    populateHttpProxyProperties(jvmArgs, proxyProperties);
  }

  @VisibleForTesting
  static void populateHttpProxyProperties(List<String> jvmArgs, List<KeyValue<String, String>> properties) {
    int propertyCount = properties.size();
    for (int i = 0; i < propertyCount; i++) {
      KeyValue<String, String> property = properties.get(i);
      String name = HTTP_PROXY_PROPERTY_PREFIX + i;
      String value = property.getKey() + HTTP_PROXY_PROPERTY_SEPARATOR + property.getValue();
      jvmArgs.add(createJvmArg(name, value));
    }
  }

  private void populateJvmArgs(@NotNull BuildSettings buildSettings, @NotNull List<String> jvmArgs) {
    BuildMode buildMode = buildSettings.getBuildMode();
    if (buildMode == null) {
      buildMode = BuildMode.DEFAULT_BUILD_MODE;
    }
    jvmArgs.add(createJvmArg(BUILD_MODE, buildMode.toString()));
    populateGradleTasksToInvoke(buildMode, jvmArgs);
  }

  @VisibleForTesting
  void populateGradleTasksToInvoke(@NotNull BuildMode buildMode, @NotNull List<String> jvmArgs) {
    if (buildMode == ASSEMBLE_TRANSLATE) {
      jvmArgs.add(createJvmArg(GRADLE_TASKS_TO_INVOKE_PROPERTY_PREFIX + 0, ASSEMBLE_TRANSLATE_TASK_NAME));
      return;
    }
    BuildSettings buildSettings = BuildSettings.getInstance(myProject);
    Module[] modulesToBuild = buildSettings.getModulesToBuild();

    if (modulesToBuild == null || (buildMode == ASSEMBLE && lastGradleSyncFailed(myProject))) {
      jvmArgs.add(createJvmArg(GRADLE_TASKS_TO_INVOKE_PROPERTY_PREFIX + 0, DEFAULT_ASSEMBLE_TASK_NAME));
      return;
    }

    GradleInvoker.TestCompileType testCompileType = GradleInvoker.getTestCompileType(buildSettings.getRunConfigurationTypeId());

    List<String> tasks = GradleInvoker.findTasksToExecute(modulesToBuild, buildMode, testCompileType);
    int taskCount = tasks.size();
    for (int i = 0; i < taskCount; i++) {
      String name = GRADLE_TASKS_TO_INVOKE_PROPERTY_PREFIX + i;
      jvmArgs.add(createJvmArg(name, tasks.get(i)));
    }
  }

  @Override
  public boolean isProcessPreloadingEnabled() {
    return !isBuildWithGradle(myProject);
  }
}
