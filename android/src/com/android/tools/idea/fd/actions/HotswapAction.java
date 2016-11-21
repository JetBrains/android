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

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.fd.InstantRunUtils;
import com.android.tools.idea.fd.IrUiExperiment;
import com.android.tools.idea.fd.gradle.InstantRunGradleUtils;
import com.android.tools.idea.gradle.actions.AndroidStudioGradleAction;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.run.ReRunAction;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.fd.gradle.InstantRunGradleSupport.SUPPORTED;

public class HotswapAction extends AndroidStudioGradleAction implements AnAction.TransparentUpdate {
  public HotswapAction() {
    super("Apply Changes", "Apply Changes", AllIcons.Actions.Lightning);
  }

  @Override
  protected void doUpdate(@NotNull AnActionEvent e, @NotNull Project project) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    if (InstantRunSettings.getUiExperimentStatus() != IrUiExperiment.HOTSWAP) {
      presentation.setVisible(false);
      return;
    }

    RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    if (settings == null) {
      return;
    }

    AndroidSessionInfo session = ReRunAction.getAndroidSessionInfo(project, settings);
    if (session == null) {
      return;
    }

    ProcessHandler processHandler = ReRunAction.getActiveProcessHandler(project, settings);
    if (processHandler == null) {
      return;
    }

    RunConfiguration configuration = settings.getConfiguration();
    if (!(configuration instanceof ModuleBasedConfiguration)) {
      return;
    }

    Module module = ((ModuleBasedConfiguration)configuration).getConfigurationModule().getModule();
    if (module == null) {
      return;
    }

    // Make sure instant run is supported on the relevant device, if found.
    AndroidVersion androidVersion = InstantRunManager.getMinDeviceApiLevel(processHandler);
    if (InstantRunManager.isInstantRunCapableDeviceVersion(androidVersion) &&
        (InstantRunGradleUtils.getIrSupportStatus(InstantRunGradleUtils.getAppModel(module), androidVersion) == SUPPORTED)) {
      presentation.setEnabled(true);
    }
  }

  @Override
  protected void doPerform(@NotNull AnActionEvent e, @NotNull Project project) {
    RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    if (settings == null) {
      InstantRunManager.LOG.warn("Hotswap Action could not locate current run config settings");
      return;
    }

    AndroidSessionInfo session = ReRunAction.getAndroidSessionInfo(project, settings);
    if (session == null) {
      InstantRunManager.LOG.warn("Hotswap Action could not locate an existing session for selected run config.");
      return;
    }

    Executor executor = ReRunAction.getExecutor(session.getExecutorId());
    if (executor == null) {
      InstantRunManager.LOG.warn("Hotswap Action could not identify executor");
      return;
    }

    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings);
    if (builder == null) {
      InstantRunManager.LOG.warn("Hotswap Action could not construct an env");
      return;
    }
    ExecutionEnvironment env = builder.activeTarget().dataContext(e.getDataContext()).build();

    InstantRunUtils.setInvokedViaAction(env, true);
    InstantRunManager.LOG.info("Invoking hotswap launch");
    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }
}
