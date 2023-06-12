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
package com.android.tools.idea.execution.common.applychanges;

import static icons.StudioIcons.Shell.Toolbar.APPLY_ALL_CHANGES;

import com.android.tools.idea.execution.common.AndroidExecutionTarget;
import com.android.tools.idea.execution.common.UtilsKt;
import com.android.tools.idea.run.util.SwapInfo;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.update.RunningApplicationUpdater;
import com.intellij.execution.update.RunningApplicationUpdaterProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import icons.StudioIcons;
import javax.swing.Icon;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ApplyChangesAction extends BaseAction {

  public static final String ID = "android.deploy.ApplyChanges";

  public static final String DISPLAY_NAME = "Apply Changes and Restart Activity";

  // The '&' is IJ markup to indicate the subsequent letter is the accelerator key.
  public static final String ACCELERATOR_NAME = "&Apply Changes and Restart Activity";

  private static final String DESC = "Attempt to apply resource and code changes and restart activity.";

  public ApplyChangesAction() {
    super(DISPLAY_NAME, ACCELERATOR_NAME, SwapInfo.SwapType.APPLY_CHANGES, APPLY_ALL_CHANGES, DESC);
  }

  @NotNull
  @Override
  public ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);

    if (!e.getPresentation().isVisible() || !e.getPresentation().isEnabled()) {
      return;
    }

    Project project = e.getProject();
    if (project == null) {
      return;
    }

    DisableMessage message = disableForTestProject(e.getProject());
    if (message != null) {
      disableAction(e.getPresentation(), message);
    }
  }

  private static DisableMessage disableForTestProject(Project project) {
    if (project == null) {
      return null;
    }

    // Disable "Apply Changes" for any kind of test project.
    RunManager runManager = RunManager.getInstanceIfCreated(project);
    if (runManager == null) {
      return null;
    }

    RunnerAndConfigurationSettings runConfig = runManager.getSelectedConfiguration();
    if (runConfig == null) return null;
    AndroidExecutionTarget selectedExecutionTarget = (AndroidExecutionTarget)ExecutionTargetManager.getActiveTarget(project);

    final List<ProcessHandler> runningProcessHandlers =
      UtilsKt.getProcessHandlersForDevices(runConfig, project, selectedExecutionTarget.getRunningDevices().stream().toList());

    final List<Executor> executors = getRunningExecutorsOfDifferentType(project, runningProcessHandlers);

    // otherwise should be disabled by [BaseAction].
    assert executors.size() <= 1;

    if (!executors.isEmpty() && executors.get(0) == DefaultDebugExecutor.getDebugExecutorInstance()) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "debug execution",
                                "it is currently not allowed during debugging");
    }
    return null;
  }

  public static class UpdaterProvider implements RunningApplicationUpdaterProvider {

    @Nullable
    @Override
    public RunningApplicationUpdater createUpdater(@NotNull Project project,
                                                   @NotNull ProcessHandler process) {

      if (getDisableMessage(project) != null || disableForTestProject(project) != null) {
        return null;
      }
      return new RunningApplicationUpdater() {
        @Override
        public String getDescription() {
          return DISPLAY_NAME;
        }

        @Override
        public String getShortName() {
          return DISPLAY_NAME;
        }

        @Override
        public Icon getIcon() {
          return StudioIcons.Shell.Toolbar.APPLY_ALL_CHANGES;
        }

        @Override
        public void performUpdate(AnActionEvent event) {
          new ApplyChangesAction().actionPerformed(event);
        }
      };
    }
  }
}

