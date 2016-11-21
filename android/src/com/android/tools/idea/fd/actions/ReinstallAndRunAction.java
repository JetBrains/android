/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.fd.actions;

import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.fd.IrUiExperiment;
import com.android.tools.idea.gradle.actions.AndroidStudioGradleAction;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.ReRunAction;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

public class ReinstallAndRunAction extends AndroidStudioGradleAction implements AnAction.TransparentUpdate {
  public ReinstallAndRunAction() {
    super("Re-install APK and Rerun", "Re-install APK and Rerun", AndroidIcons.RunIcons.ReInstallAndRun);
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    if (InstantRunSettings.getUiExperimentStatus() != IrUiExperiment.STOP_AND_RUN) {
      presentation.setVisible(false);
      return;
    }

    RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    if (settings == null) {
      InstantRunManager.LOG.warn("Re-install and run Action could not locate current run config settings");
      return;
    }

    presentation.setEnabled(true);
  }

  // Hotswap action requires an existing session. This action doesn't. If there is an existing session, then
  // we make use of the executor from that session, otherwise we'll just default to Run.
  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    if (settings == null) {
      InstantRunManager.LOG.warn("Re-install and run Action could not locate current run config settings");
      return;
    }

    Executor executor = null;
    AndroidSessionInfo session = ReRunAction.getAndroidSessionInfo(project, settings);
    if (session != null) {
      executor = ReRunAction.getExecutor(session.getExecutorId());
    }

    if (executor == null) {
      executor = DefaultRunExecutor.getRunExecutorInstance();
    }

    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings);
    if (builder == null) {
      InstantRunManager.LOG.warn("Re-install and run Action could not construct an env");
      return;
    }
    ExecutionEnvironment env = builder.activeTarget().dataContext(e.getDataContext()).build();

    InstantRunUtils.setInvokedViaAction(env, true);
    InstantRunManager.LOG.info("Invoking re-install and run launch");
    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }
}
