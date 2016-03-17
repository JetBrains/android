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
package com.android.tools.idea.run;

import com.android.tools.idea.fd.InstantRunManager;
import com.android.tools.idea.fd.InstantRunUtils;
import com.intellij.execution.*;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

/**
 * {@link ReRunAction} will terminate and run the currently selected and running configuration.
 * Under certain circumstances (see {@link #isCleanBuild(AnActionEvent)}), it will also do a clean before the run.
 */
public class ReRunAction extends DumbAwareAction implements AnAction.TransparentUpdate {
  public ReRunAction() {
    super("Rerun", "ReRun Selected Configuration", AllIcons.Actions.Restart);
  }

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    boolean cleanBuild = isCleanBuild(e);

    RunContentDescriptor selectedContent = getSelectedRunContentDescriptor(e.getDataContext());
    if (selectedContent == null) {
      disable(cleanBuild, presentation);
      return;
    }

    ProcessHandler processHandler = selectedContent.getProcessHandler();
    if (processHandler == null || processHandler.isProcessTerminated() || processHandler.isProcessTerminating()) {
      disable(cleanBuild, presentation);
      return;
    }

    presentation.setEnabled(true);
    String prefix = cleanBuild ? "Clean and " : "";
    presentation.setText(prefix + "Rerun '" + selectedContent.getDisplayName() + "'");
  }

  private static void disable(boolean cleanBuild, @NotNull Presentation presentation) {
    presentation.setEnabled(false);

    String text = cleanBuild ? "Clean and Rerun" : "Rerun";
    presentation.setText(text);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    if (project == null) {
      return;
    }

    RunnerAndConfigurationSettings settings = RunManagerEx.getInstanceEx(project).getSelectedConfiguration();
    if (settings == null) {
      InstantRunManager.LOG.warn("Rerun could not locate current run config settings");
      return;
    }

    RunConfiguration configuration = settings.getConfiguration();
    if (configuration == null) {
      InstantRunManager.LOG.warn("Rerun could not locate current run config");
      return;
    }

    AndroidSessionInfo session = AndroidSessionInfo.findOldSession(project, null, configuration.getUniqueID());
    if (session == null) {
      InstantRunManager.LOG.warn("Rerun could not locate existing session of this configuration");
      return;
    }

    Executor executor = getExecutor(session.getExecutorId());
    if (executor == null) {
      InstantRunManager.LOG.warn("Rerun could not identify executor for rerun");
      return;
    }

    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.createOrNull(executor, settings);
    if (builder == null) {
      InstantRunManager.LOG.warn("Rerun could not construct an env");
      return;
    }
    ExecutionEnvironment env = builder.activeTarget().dataContext(e.getDataContext()).build();

    InstantRunUtils.setReRun(env, true);

    boolean cleanBuild = isCleanBuild(e);
    InstantRunUtils.setCleanReRun(env, cleanBuild);

    InstantRunManager.LOG.info("Rerun: clean? " + cleanBuild);
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

  private static boolean isCleanBuild(@NotNull AnActionEvent e) {
    // invoked from the menu
    if (ActionPlaces.isMainMenuOrActionSearch(e.getPlace())) {
      return true;
    }

    // or Shift was pressed while clicking on this action
    if (e.getInputEvent() instanceof MouseEvent) {
      if ((e.getModifiers() & InputEvent.SHIFT_MASK) == InputEvent.SHIFT_MASK) {
        return true;
      }
    }

    return false;
  }

  private static RunContentDescriptor getSelectedRunContentDescriptor(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }

    return ExecutionManager.getInstance(project).getContentManager().getSelectedContent();
  }
}
