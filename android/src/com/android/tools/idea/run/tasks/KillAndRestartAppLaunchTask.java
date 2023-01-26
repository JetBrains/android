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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.ApplicationTerminator;
import com.android.tools.idea.execution.common.processhandler.AndroidProcessHandler;
import com.android.tools.idea.run.ui.DeployAction;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.playback.commands.ActionCommand;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KillAndRestartAppLaunchTask implements LaunchTask {
  private static final String ID = "KILL_APPLICATION";

  private final String myPackageName;

  public KillAndRestartAppLaunchTask(@NotNull String packageName) {
    myPackageName = packageName;
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Killing application";
  }

  @Override
  public int getDuration() {
    return LaunchTaskDurations.LAUNCH_ACTIVITY;
  }

  @Override
  public boolean shouldRun(@NotNull LaunchContext launchContext) {
    return launchContext.getKillBeforeLaunch();
  }

  @Override
  public void run(@NotNull LaunchContext launchContext) throws ExecutionException {
    boolean terminateApp;

    ProcessHandler handler = launchContext.getProcessHandler();
    IDevice device = launchContext.getDevice();
    if (handler instanceof AndroidProcessHandler) {
      AndroidProcessHandler androidHandler = (AndroidProcessHandler)launchContext.getProcessHandler();
      // AndroidProcessHandler already does a hard kill, so we only need to do manual kill if the app somehow disappeared
      // in the time window. However, we need to use killClientAndreAddTargetDevice since by this time the ProcessHandler
      // tied to the tool window is final for this run, so we have to reuse the given ProcessHandler.
      terminateApp = !androidHandler.killClientAndRestartMonitor(device);
    }
    else {
      // Fallback to rerun for all other handlers, since it is an IJ class and not related to AndroidProcessHandler.
      handler.destroyProcess();
      // Remote debugger only invokes VM_EXIT, so we an extra terminate step since app might be in crashed state.
      terminateApp = true;
    }

    if (!terminateApp) {
      return;
    }

    // Ensure the app is killed (otherwise launch won't work).
    ApplicationTerminator appTerminator = new ApplicationTerminator(device, myPackageName);
    if (!appTerminator.killApp()) {
      throw new ExecutionException("Fail to terminate app prior to restarting.");
    }

    if (device.isOnline() && handler instanceof AndroidProcessHandler) {
      // Add the device back to the existing process handler.
      ((AndroidProcessHandler)launchContext.getProcessHandler()).addTargetDevice(device);
    }
    else {
      ApplicationManager.getApplication().invokeLater(() -> {
        ActionManager manager = ActionManager.getInstance();
        String id;
        if (DefaultRunExecutor.getRunExecutorInstance().getId().equals(launchContext.getExecutor().getId())) {
          id = DeployAction.RunWithoutBuildAction.ID;
        }
        else if (DefaultDebugExecutor.getDebugExecutorInstance().getId().equals(launchContext.getExecutor().getId())) {
          id = DeployAction.DebugWithoutBuildAction.ID;
        }
        else {
          // We don't support anything outside of the default Run/Debug.
          return;
        }

        AnAction action = manager.getAction(id);
        if (action == null) {
          return;
        }

        Project project = launchContext.getProject();
        JComponent contextComponent = new JComponent() {};
        contextComponent.setVisible(true);
        contextComponent.setEnabled(true);
        contextComponent.addNotify();
        DataManager.registerDataProvider(contextComponent, new RerunDataProvider(project));
        manager.tryToExecute(action, ActionCommand.getInputEvent(id), contextComponent, ActionPlaces.UNKNOWN, true);
      });
      throw new ExecutionException("Swap failed, need to rerun.");
    }
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
  }

  private static final class RerunDataProvider implements DataProvider {
    private final Project myProject;

    private RerunDataProvider(@NotNull Project project) {
      myProject = project;
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      if (CommonDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }

      return null;
    }
  }
}
