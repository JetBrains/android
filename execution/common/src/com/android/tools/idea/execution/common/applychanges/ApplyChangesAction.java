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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.AndroidExecutionTarget;
import com.android.tools.idea.execution.common.AndroidSessionInfo;
import com.android.tools.idea.run.util.SwapInfo;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.xdebugger.XDebuggerManager;
import java.util.Arrays;
import java.util.List;
import javax.swing.KeyStroke;
import org.jetbrains.annotations.NotNull;

public class ApplyChangesAction extends BaseAction {

  public static final String ID = "android.deploy.ApplyChanges";

  public static final String DISPLAY_NAME = "Apply Changes and Restart Activity";

  // The '&' is IJ markup to indicate the subsequent letter is the accelerator key.
  public static final String ACCELERATOR_NAME = "&Apply Changes and Restart Activity";

  private static final Shortcut SHORTCUT =
    new KeyboardShortcut(KeyStroke.getKeyStroke(SystemInfo.isMac ? "control meta E" : "control F10"), null);

  private static final String DESC = "Attempt to apply resource and code changes and restart activity.";

  public ApplyChangesAction() {
    super(ID, DISPLAY_NAME, ACCELERATOR_NAME, SwapInfo.SwapType.APPLY_CHANGES, APPLY_ALL_CHANGES, SHORTCUT, DESC);
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

    AndroidExecutionTarget selectedExecutionTarget = (AndroidExecutionTarget)ExecutionTargetManager.getActiveTarget(project);

    final List<IDevice> devices = selectedExecutionTarget.getRunningDevices().stream().toList();

    final List<ProcessHandler> debugProcessHandlers =
      Arrays.stream(XDebuggerManager.getInstance(project).getDebugSessions()).map(x -> x.getDebugProcess().getProcessHandler()).toList();

    final boolean debuggerConnected = debugProcessHandlers.stream().anyMatch(processHandler -> {
      final AndroidSessionInfo sessionInfo = AndroidSessionInfo.Companion.from(processHandler);
      if (sessionInfo == null) {
        return false;
      }
      return devices.stream().anyMatch(device -> sessionInfo.getDevices().contains(device));
    });

    if (debuggerConnected) {
      disableAction(e.getPresentation(), new DisableMessage(DisableMessage.DisableMode.DISABLED, "debug execution",
                                                            "it is currently not allowed during debugging"));
    }
  }
}

