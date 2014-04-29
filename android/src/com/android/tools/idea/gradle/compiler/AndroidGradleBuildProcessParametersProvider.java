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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.sdk.DefaultSdks;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.execution.configurations.CommandLineTokenizer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.KeyValue;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.net.HttpConfigurable;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static com.android.tools.idea.gradle.compiler.BuildProcessJvmArgs.*;
import static com.android.tools.idea.gradle.util.AndroidGradleSettings.createJvmArg;

/**
 * Adds Gradle jars to the build process' classpath and adds extra Gradle-related configuration options.
 */
public class AndroidGradleBuildProcessParametersProvider extends BuildProcessParametersProvider {
  @NotNull private final Project myProject;

  private List<String> myClasspath;

  public AndroidGradleBuildProcessParametersProvider(@NotNull Project project) {
    myProject = project;
  }

  /**
   * Adds Gradle to the build process' classpath.
   *
   * @return a list containing the locations of all jars necessary to include Gradle in the classpath.
   */
  @Override
  @NotNull
  public List<String> getClassPath() {
    if (myClasspath == null) {
      myClasspath = getGradleClassPath();
    }
    return myClasspath;
  }

  @NotNull
  private static List<String> getGradleClassPath() {
    String gradleLibDirPath = null;
    String gradleToolingApiJarPath = PathUtil.getJarPathForClass(ProjectConnection.class);
    if (!Strings.isNullOrEmpty(gradleToolingApiJarPath)) {
      gradleLibDirPath = PathUtil.getParentPath(gradleToolingApiJarPath);
    }
    if (gradleLibDirPath == null || gradleLibDirPath.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> classpath = Lists.newArrayList();
    File gradleLibDir = new File(gradleLibDirPath);
    if (!gradleLibDir.isDirectory()) {
      return Collections.emptyList();
    }
    File[] children = FileUtil.notNullize(gradleLibDir.listFiles());
    for (File child : children) {
      if (child.isFile() && child.getName().endsWith(SdkConstants.DOT_JAR)) {
        classpath.add(child.getAbsolutePath());
      }
    }
    return classpath;
  }

  @Override
  @NotNull
  public List<String> getVMArguments() {
    if (!isBuildByGradle(myProject)) {
      return Collections.emptyList();
    }
    List<String> jvmArgs = Lists.newArrayList();

    AndroidGradleBuildConfiguration buildConfiguration = AndroidGradleBuildConfiguration.getInstance(myProject);
    populateJvmArgs(buildConfiguration, jvmArgs, myProject);

    GradleExecutionSettings executionSettings = GradleUtil.getGradleExecutionSettings(myProject);
    if (executionSettings != null) {
      populateJvmArgs(executionSettings, jvmArgs);
    }

    populateJvmArgs(BuildSettings.getInstance(myProject), jvmArgs);

    addHttpProxySettings(jvmArgs);
    return jvmArgs;
  }

  private static boolean isBuildByGradle(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      if (AndroidGradleFacet.getInstance(module) != null) {
        return true;
      }
    }
    return false;
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
    jvmArgs.add(createJvmArg(GRADLE_DAEMON_COMMAND_LINE_OPTION_COUNT, commandLineOptions.length));
    int optionCount = 0;
    for (String option : commandLineOptions) {
      String name = GRADLE_DAEMON_COMMAND_LINE_OPTION_PREFIX + optionCount;
      jvmArgs.add(createJvmArg(name, option));
      optionCount++;
    }
  }

  @VisibleForTesting
  void populateJvmArgs(@NotNull GradleExecutionSettings executionSettings, @NotNull List<String> jvmArgs) {
    long daemonMaxIdleTimeInMs = executionSettings.getRemoteProcessIdleTtlInMs();
    jvmArgs.add(createJvmArg(GRADLE_DAEMON_MAX_IDLE_TIME_IN_MS, String.valueOf(daemonMaxIdleTimeInMs)));

    String gradleHome = executionSettings.getGradleHome();
    if (gradleHome != null && !gradleHome.isEmpty()) {
      gradleHome = FileUtil.toSystemDependentName(gradleHome);
      jvmArgs.add(createJvmArg(GRADLE_HOME_DIR_PATH, gradleHome));
    }

    String serviceDirectory = executionSettings.getServiceDirectory();
    if (serviceDirectory != null && !serviceDirectory.isEmpty()) {
      serviceDirectory = FileUtil.toSystemDependentName(serviceDirectory);
      jvmArgs.add(createJvmArg(GRADLE_SERVICE_DIR_PATH, serviceDirectory));
    }

    File javaHome = DefaultSdks.getDefaultJavaHome();
    if (javaHome != null) {
      jvmArgs.add(createJvmArg(GRADLE_JAVA_HOME_DIR_PATH, javaHome.getPath()));
    }

    String basePath = FileUtil.toSystemDependentName(myProject.getBasePath());
    jvmArgs.add(createJvmArg(PROJECT_DIR_PATH, basePath));

    boolean verboseProcessing = executionSettings.isVerboseProcessing();
    jvmArgs.add(createJvmArg(USE_GRADLE_VERBOSE_LOGGING, verboseProcessing));

    String jvmOptions = executionSettings.getDaemonVmOptions();
    int jvmOptionCount = 0;
    if (jvmOptions != null && !jvmOptions.isEmpty()) {
      CommandLineTokenizer tokenizer = new CommandLineTokenizer(jvmOptions);
      while(tokenizer.hasMoreTokens()) {
        String name = GRADLE_DAEMON_JVM_OPTION_PREFIX + jvmOptionCount;
        jvmArgs.add(createJvmArg(name, tokenizer.nextToken()));
        jvmOptionCount++;
      }
    }
    jvmArgs.add(createJvmArg(GRADLE_DAEMON_JVM_OPTION_COUNT, jvmOptionCount));
  }

  private static void addHttpProxySettings(@NotNull List<String> jvmArgs) {
    List<KeyValue<String, String>> proxyProperties = HttpConfigurable.getJvmPropertiesList(false, null);
    populateHttpProxyProperties(jvmArgs, proxyProperties);
  }

  @VisibleForTesting
  static void populateHttpProxyProperties(List<String> jvmArgs, List<KeyValue<String, String>> properties) {
    int propertyCount = properties.size();
    jvmArgs.add(createJvmArg(HTTP_PROXY_PROPERTY_COUNT, propertyCount));

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
    populateModulesToBuild(buildMode, jvmArgs);
  }

  @VisibleForTesting
  void populateModulesToBuild(@NotNull BuildMode buildMode, @NotNull List<String> jvmArgs) {
    String[] modulesToBuild = getModulesToBuild(buildMode);
    int moduleCount = modulesToBuild == null ? 0 : modulesToBuild.length;
    jvmArgs.add(createJvmArg(MODULES_TO_BUILD_PROPERTY_COUNT, moduleCount));
    for (int i = 0; i < moduleCount; i++) {
      String name = MODULES_TO_BUILD_PROPERTY_PREFIX + i;
      jvmArgs.add(createJvmArg(name, modulesToBuild[i]));
    }
  }

  @Nullable
  private String[] getModulesToBuild(@NotNull BuildMode buildMode) {
    if (buildMode.equals(BuildMode.ASSEMBLE_TRANSLATE) || (buildMode.equals(BuildMode.ASSEMBLE) && Projects.lastGradleSyncFailed(myProject))) {
      return null;
    }
    BuildSettings buildSettings = BuildSettings.getInstance(myProject);
    return buildSettings.getModulesToBuildNames();
  }
}
