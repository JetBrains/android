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
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.intellij.compiler.server.BuildProcessParametersProvider;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Adds Gradle jars to the build process' classpath and adds extra Gradle-related configuration options.
 */
public class AndroidGradleBuildProcessParametersProvider extends BuildProcessParametersProvider {
  private static final Logger LOG = Logger.getInstance(AndroidGradleBuildProcessParametersProvider.class);
  private static final ProjectSystemId SYSTEM_ID = GradleConstants.SYSTEM_ID;

  @NotNull private final Project myProject;

  private List<String> myClasspath;

  @NonNls private static final String JVM_ARG_FORMAT = "-D%1$s=%2$s";
  @NonNls private static final String JVM_ARG_WITH_QUOTED_VALUE_FORMAT = "-D%1$s=\"%2$s\"";

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
    if (Strings.isNullOrEmpty(gradleLibDirPath)) {
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
    if (!Projects.isGradleProject(myProject)) {
      return Collections.emptyList();
    }
    ExternalSystemSettingsManager settingsManager = ServiceManager.getService(ExternalSystemSettingsManager.class);
    GradleSettings settings = (GradleSettings)settingsManager.getSettings(myProject, SYSTEM_ID);

    GradleSettings.MyState state = settings.getState();
    assert state != null;
    Set<GradleProjectSettings> allProjectsSettings = state.getLinkedExternalProjectsSettings();

    GradleProjectSettings projectSettings = getFirstNotNull(allProjectsSettings);
    if (projectSettings == null) {
      String format = "Unable to obtain Gradle project settings for project '%1$s', located at '%2$s'";
      String msg = String.format(format, myProject.getName(), myProject.getBasePath());
      LOG.info(msg);
      return Collections.emptyList();
    }
    List<String> jvmArgs = Lists.newArrayList();

    String projectPath = projectSettings.getExternalProjectPath();
    GradleExecutionSettings executionSettings = settingsManager.getExecutionSettings(myProject, projectPath, SYSTEM_ID);
    populateJvmArgs(executionSettings, jvmArgs);

    GradleCompilerConfiguration compilerConfiguration = GradleCompilerConfiguration.getInstance(myProject);
    populateJvmArgs(compilerConfiguration, jvmArgs);

    return jvmArgs;
  }

  @Nullable
  private static GradleProjectSettings getFirstNotNull(Set<GradleProjectSettings> allProjectSettings) {
    for (GradleProjectSettings settings : allProjectSettings) {
      if (settings != null) {
        return settings;
      }
    }
    return null;
  }

  @VisibleForTesting
  void populateJvmArgs(@NotNull GradleExecutionSettings executionSettings, @NotNull List<String> jvmArgs) {
    long daemonMaxIdleTimeInMs = executionSettings.getRemoteProcessIdleTtlInMs();
    jvmArgs.add(createJvmArg(BuildProcessJvmArgs.GRADLE_DAEMON_MAX_IDLE_TIME_IN_MS, String.valueOf(daemonMaxIdleTimeInMs)));

    String gradleHome = executionSettings.getGradleHome();
    if (gradleHome != null && !gradleHome.isEmpty()) {
      jvmArgs.add(createJvmArg(BuildProcessJvmArgs.GRADLE_HOME_DIR_PATH, gradleHome));
    }

    String serviceDirectory = executionSettings.getServiceDirectory();
    if (serviceDirectory != null && !serviceDirectory.isEmpty()) {
      jvmArgs.add(createJvmArg(BuildProcessJvmArgs.GRADLE_SERVICE_DIR_PATH, serviceDirectory));
    }

    jvmArgs.add(createJvmArg(BuildProcessJvmArgs.PROJECT_DIR_PATH, myProject.getBasePath()));

    boolean verboseProcessing = executionSettings.isVerboseProcessing();
    jvmArgs.add(createJvmArg(BuildProcessJvmArgs.USE_GRADLE_VERBOSE_LOGGING, String.valueOf(verboseProcessing)));
  }

  @VisibleForTesting
  void populateJvmArgs(GradleCompilerConfiguration compilerConfiguration, List<String> jvmArgs) {
    jvmArgs.add(createJvmArg(BuildProcessJvmArgs.GRADLE_DAEMON_MAX_MEMORY_IN_MB, compilerConfiguration.MAX_HEAP_SIZE));
    jvmArgs.add(createJvmArg(BuildProcessJvmArgs.GRADLE_DAEMON_MAX_PERM_GEN_IN_MB, compilerConfiguration.MAX_PERM_GEN_SIZE));
  }

  @NotNull
  private static String createJvmArg(@NotNull String name, int value) {
    return createJvmArg(name, String.valueOf(value));
  }

  @NotNull
  private static String createJvmArg(@NotNull String name, @NotNull String value) {
    String format = JVM_ARG_FORMAT;
    if (value.contains(" ")) {
      format = JVM_ARG_WITH_QUOTED_VALUE_FORMAT;
    }
    return String.format(format, name, value);
  }
}
