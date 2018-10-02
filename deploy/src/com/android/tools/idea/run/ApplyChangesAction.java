/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import icons.StudioIcons;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApplyChangesAction extends AnAction {

  public static final Logger LOG = Logger.getInstance(ApplyChangesAction.class);

  public static final Key<Boolean> APPLY_CHANGES = Key.create("android.apply.changes");

  private static final CustomShortcutSet SHORTCUT_SET = CustomShortcutSet.fromString(SystemInfo.isMac ? "control meta R" : "control F10");

  @NotNull private final Function<Project, Boolean> myShouldEnableApplyChangesProvider;

  public ApplyChangesAction(@NotNull Function<Project, Boolean> shouldEnableApplyChangesProvider) {
    super("Apply Changes", "Apply Changes", StudioIcons.Shell.Toolbar.INSTANT_RUN);
    myShouldEnableApplyChangesProvider = shouldEnableApplyChangesProvider;
    setShortcutSet(SHORTCUT_SET);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (e.getProject() == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    Presentation presentation = e.getPresentation();
    presentation.setEnabled(myShouldEnableApplyChangesProvider.apply(e.getProject()));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      LOG.warn("Apply Changes action performed with no project");
      return;
    }

    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
    if (settings == null) {
      LOG.warn("Apply Changes action could not locate current run config settings");
      return;
    }

    // TODO: Figure out the debugger flow. For now always use the Run executor.
    Executor executor = getExecutor(DefaultRunExecutor.EXECUTOR_ID);
    if (executor == null) {
      LOG.warn("Apply Changes action could not identify executor");
      return;
    }

    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings);
    if (builder == null) {
      LOG.warn("Apply Changes action could not construct an env");
      return;
    }
    ExecutionEnvironment env = builder.activeTarget().dataContext(e.getDataContext()).build();

    env.putCopyableUserData(APPLY_CHANGES, true);
    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  @Nullable
  private static Executor getExecutor(@NotNull String executorId) {
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensions()) {
      if (executorId.equals(executor.getId())) {
        return executor;
      }
    }

    return null;
  }
}

