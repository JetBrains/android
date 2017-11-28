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
package com.android.tools.idea.gradle.task;

import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.util.Projects;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.task.GradleTaskManagerExtension;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

<<<<<<< HEAD
=======
import static com.android.tools.idea.gradle.util.Projects.isDirectGradleInvocationEnabled;
import static org.jetbrains.plugins.gradle.service.task.GradleTaskManager.appendInitScriptArgument;

>>>>>>> goog/upstream-ij17
/**
 * Executes Gradle tasks.
 */
public class AndroidGradleTaskManager implements GradleTaskManagerExtension {

  /**
   * @deprecated use {@link #executeTasks(ExternalSystemTaskId, List, String, GradleExecutionSettings, String, ExternalSystemTaskNotificationListener)}
   */
  @Override
  public boolean executeTasks(@NotNull ExternalSystemTaskId id,
                              @NotNull List<String> taskNames,
                              @NotNull String projectPath,
                              @Nullable GradleExecutionSettings settings,
                              @NotNull List<String> vmOptions,
                              @NotNull List<String> scriptParameters,
                              @Nullable String debuggerSetup,
                              @NotNull ExternalSystemTaskNotificationListener listener) throws ExternalSystemException {
    GradleExecutionSettings effectiveSettings =
      settings == null ? new GradleExecutionSettings(null, null, DistributionType.BUNDLED, false) : settings;
    effectiveSettings
      .withVmOptions(vmOptions)
      .withArguments(scriptParameters);
    return executeTasks(id, taskNames, projectPath, effectiveSettings, debuggerSetup, listener);
  }

  @Override
  public boolean executeTasks(@NotNull final ExternalSystemTaskId id,
                              @NotNull final List<String> taskNames,
                              @NotNull String projectPath,
                              @Nullable GradleExecutionSettings settings,
                              @Nullable final String jvmAgentSetup,
                              @NotNull final ExternalSystemTaskNotificationListener listener) throws ExternalSystemException {
    GradleBuildInvoker gradleBuildInvoker = findGradleInvoker(id, projectPath);
    if (gradleBuildInvoker != null) {
      GradleBuildInvoker.Request request =
        new GradleBuildInvoker.Request(gradleBuildInvoker.getProject(), new File(projectPath), taskNames, id);

      GradleExecutionSettings effectiveSettings =
        settings == null ? new GradleExecutionSettings(null, null, DistributionType.BUNDLED, false) : settings;
      appendInitScriptArgument(taskNames, jvmAgentSetup, effectiveSettings);
      // @formatter:off
      request.setJvmArguments(new ArrayList<>(effectiveSettings.getVmOptions()))
             .setCommandLineArguments(effectiveSettings.getArguments())
             .withEnvironmentVariables(effectiveSettings.getEnv())
             .passParentEnvs(effectiveSettings.isPassParentEnvs())
             .setTaskListener(listener)
             .waitForCompletion();
      // @formatter:on

      gradleBuildInvoker.executeTasks(request);
      return true;
    }
    // Returning false gives control back to the framework, and the task(s) will be invoked by IDEA.
    return false;
  }

  @Override
  public boolean cancelTask(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemTaskNotificationListener listener) {
    Project project = id.findProject();
    if (project != null) {
      return GradleBuildInvoker.getInstance(project).stopBuild(id);
    }
    return false;
  }

  @Nullable
  private static GradleBuildInvoker findGradleInvoker(ExternalSystemTaskId id, String projectPath) {
    Project project = id.findProject();
<<<<<<< HEAD
    if (project != null &&
        AndroidProjectInfo.getInstance(project).requiresAndroidModel() &&
        GradleProjectInfo.getInstance(project).isDirectGradleBuildEnabled()) {
      return GradleBuildInvoker.getInstance(project);
=======
    if (project != null && isDirectGradleInvocationEnabled(project)) {
      ModuleManager moduleManager = ModuleManager.getInstance(project);
      for (Module module : moduleManager.getModules()) {
        if (projectPath.equals(ExternalSystemApiUtil.getExternalProjectPath(module)) && Projects.isIdeaAndroidModule(module)) {
          return GradleBuildInvoker.getInstance(project);
        }
      }
>>>>>>> goog/upstream-ij17
    }
    return null;
  }
}
