/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run.ui;

import static com.intellij.execution.executors.DefaultDebugExecutor.getDebugExecutorInstance;
import static com.intellij.execution.executors.DefaultRunExecutor.getRunExecutorInstance;

import com.android.tools.idea.gradle.util.GradleBuilds;
import com.android.tools.idea.util.CommonAndroidUtil;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.compound.CompoundRunConfiguration;
import com.intellij.execution.compound.SettingsAndEffectiveTarget;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DeployAction extends AnAction {
  @NotNull private final String myId;
  @NotNull private final Executor myExecutor;
  @NotNull private final String myEnabledText;
  @NotNull private final String myEnabledDescription;

  protected DeployAction(@NotNull String id, @NotNull Executor executor, @NotNull String text, @NotNull String description) {
    super(text, description, null);
    myId = id;
    myExecutor = executor;
    myEnabledText = text;
    myEnabledDescription = description;
  }

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project == null) {
      disableAction(presentation, "Lack of Project", "Disabled due to lack of project");
      return;
    }

    if (!CommonAndroidUtil.getInstance().isAndroidProject(project)) {
      disableAction(presentation, "Not an Android Project", "Disabled since this Project is not an Android project.");
      return;
    }

    // TODO: Check if the built APK is present.

    presentation.setVisible(true);
    presentation.setEnabled(true);
    presentation.setText(myEnabledText);
    presentation.setDescription(myEnabledDescription);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }

    RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(project).getSelectedConfiguration();
    if (selectedConfiguration == null) {
      return;
    }

    RunConfiguration runConfiguration = selectedConfiguration.getConfiguration();
    rerun(project, myExecutor, runConfiguration, selectedConfiguration, e.getDataContext());
  }

  @Override
  public boolean isDumbAware() {
    // Action is dumb-aware since we don't need to build.
    return true;
  }

  @NotNull
  public String getId() {
    return myId;
  }

  private static void disableAction(@NotNull Presentation presentation, @NotNull String text, @NotNull String description) {
    presentation.setVisible(false);
    presentation.setEnabled(false);
    presentation.setText(text);
    presentation.setDescription(description);
  }

  /**
   * This is code lifted out of {@link com.intellij.execution.ExecutorRegistryImpl.ExecutorAction#run}.
   *
   * All it does is check if the given {@link RunConfiguration} is a {@link CompoundRunConfiguration},
   * and if so, recursively invokes itself on constituent {@link RunConfiguration}. If not, it will
   * just invoke {@link ExecutionManager#restartRunProfile(ExecutionEnvironment)}.
   *
   * The only difference between in this version is that we add an extra "do not build" flag to the
   * created {@link ExecutionEnvironment}'s {@link com.intellij.openapi.util.UserDataHolder}.
   */
  private static void rerun(@NotNull Project project,
                            @NotNull Executor executor,
                            @Nullable RunConfiguration configuration,
                            @Nullable RunnerAndConfigurationSettings settings,
                            @NotNull DataContext dataContext) {
    if (configuration instanceof CompoundRunConfiguration) {
      RunManager runManager = RunManager.getInstance(project);
      for (SettingsAndEffectiveTarget settingsAndEffectiveTarget : ((CompoundRunConfiguration)configuration)
        .getConfigurationsWithEffectiveRunTargets()) {
        RunConfiguration subConfiguration = settingsAndEffectiveTarget.getConfiguration();
        rerun(project, executor, subConfiguration, runManager.findSettings(subConfiguration), dataContext);
      }
    }
    else {
      ExecutionEnvironmentBuilder builder = settings == null ? null : ExecutionEnvironmentBuilder.createOrNull(executor, settings);
      if (builder == null) {
        return;
      }
      ExecutionEnvironment env = builder.activeTarget().dataContext(dataContext).build();
      env.putUserData(GradleBuilds.BUILD_SHOULD_EXECUTE, false);
      ExecutionManager.getInstance(project).restartRunProfile(env);
    }
  }

  public static final class RunWithoutBuildAction extends DeployAction {
    public static final String ID = "android.deploy.RunWithoutBuild";

    public RunWithoutBuildAction() {
      super(ID, getRunExecutorInstance(), "Run Without Build", "Deploys and runs the application without building.");
    }
  }

  public static final class DebugWithoutBuildAction extends DeployAction {
    public static final String ID = "android.deploy.DebugWithoutBuild";

    public DebugWithoutBuildAction() {
      super(ID, getDebugExecutorInstance(), "Debug Without Build", "Deploys and debugs the application without building.");
    }
  }
}
