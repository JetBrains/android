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
package com.android.tools.idea.run.ui;

import static com.android.tools.idea.run.tasks.AbstractDeployTask.MIN_API_VERSION;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.run.DeploymentService;
import com.android.tools.idea.run.deployable.Deployable;
import com.android.tools.idea.run.deployable.DeployableProvider;
import com.android.tools.idea.run.deployable.SwappableProcessHandler;
import com.intellij.debugger.engine.RemoteDebugProcessHandler;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(BaseAction.class);
  public static final Key<Boolean> SHOW_APPLY_CHANGES_UI = Key.create("android.deploy.ApplyChanges.ShowUI");

  @NotNull
  protected final String myName;

  @NotNull
  protected final Icon myIcon;

  @NotNull
  private final Key<Boolean> myKey;

  @NotNull
  private final String myDescription;

  public BaseAction(@NotNull String id,
                    @NotNull String name,
                    @NotNull Key<Boolean> key,
                    @NotNull Icon icon,
                    @NotNull Shortcut shortcut,
                    @NotNull String description) {
    super(name, description, icon);
    myName = name;
    myKey = key;
    myIcon = icon;
    myDescription = description;

    KeymapManager manager = KeymapManager.getInstance();
    if (manager != null) {
      final Keymap keymap = manager.getActiveKeymap();
      if (keymap != null) {
        List<Shortcut> shortcuts = Arrays.asList(keymap.getShortcuts(id));
        if (shortcuts.isEmpty()) {
          // Add the shortcut for the first time.
          // TODO: figure out how to not add it back if the user deliberately removes the action hotkey.
          keymap.addShortcut(id, shortcut);
          shortcuts = Collections.singletonList(shortcut);
        }

        // Remove conflicting shortcuts stemming from UpdateRunningApplication only,
        // and leave the remaining conflicts intact, since that's what the user intends.
        final String updateRunningApplicationId = "UpdateRunningApplication";
        Shortcut[] uraShortcuts = keymap.getShortcuts(updateRunningApplicationId);
        for (Shortcut uraShortcut : uraShortcuts) {
          if (shortcuts.contains(uraShortcut)) {
            keymap.removeShortcut(updateRunningApplicationId, uraShortcut);
          }
        }
      }
    }
  }

  /**
   * Apply Changes UI is:
   * - Visible if it's relevant to the currently selected run configuration.  E.g. Apply Changes UI is irrelevant
   *   if the run configuration isn't an android app run configuration or android test configuration.
   * - Visible and enabled if it's applicable to the current run configuration and the project is compatible.
   */
  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project == null) {
      presentation.setVisible(false);
      return;
    }

    DisableMessage disableMessage = getDisableMessage(project);
    if (disableMessage == null) {
      presentation.setVisible(true);
      presentation.setEnabled(true);
      presentation.setText(myName);
      presentation.setDescription(myDescription);
    }
    else {
      disableAction(presentation, disableMessage);
    }
  }

  @Nullable
  public static DisableMessage getDisableMessage(@NotNull Project project) {
    RunnerAndConfigurationSettings configSettings = RunManager.getInstance(project).getSelectedConfiguration();
    if (configSettings == null) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "no configuration selected", "there is no configuration selected");
    }

    RunConfiguration selectedRunConfig = configSettings.getConfiguration();
    if (!isApplyChangesRelevant(selectedRunConfig)) {
      return new DisableMessage(DisableMessage.DisableMode.INVISIBLE, "unsupported configuration",
                                "the selected configuration is not supported");
    }

    if (isExecutorStarting(project, selectedRunConfig)) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "building and/or launching",
                                "the selected configuration is currently building and/or launching");
    }

    DeployableProvider deployableProvider = DeploymentService.getInstance(project).getDeployableProvider();
    if (deployableProvider == null) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "no deployment provider",
                                "there is no deployment provider specified");
    }

    if (!deployableProvider.isDependentOnUserInput()) {
      Deployable deployable;
      try {
        deployable = deployableProvider.getDeployable();
        if (deployable == null) {
          return new DisableMessage(DisableMessage.DisableMode.DISABLED, "selected device is invalid", "the selected device is not valid");
        }

        if (!deployable.isOnline()) {
          if (deployable.isUnauthorized()) {
            return new DisableMessage(DisableMessage.DisableMode.DISABLED, "device not authorized",
                                      "the selected device is not authorized");
          }
          else {
            return new DisableMessage(DisableMessage.DisableMode.DISABLED, "device not connected", "the selected device is not connected");
          }
        }

        Future<AndroidVersion> versionFuture = deployable.getVersion();
        if (!versionFuture.isDone()) {
          // Don't stall the EDT - if the Future isn't ready, just return false.
          return new DisableMessage(DisableMessage.DisableMode.DISABLED, "unknown device API level", "its API level is currently unknown");
        }

        if (versionFuture.get().getApiLevel() < MIN_API_VERSION) {
          return new DisableMessage(DisableMessage.DisableMode.DISABLED, "incompatible device API level",
                                    "its API level is lower than 26");
        }

        if (!deployable.isApplicationRunningOnDeployable()) {
          return new DisableMessage(DisableMessage.DisableMode.DISABLED, "app not detected",
                                    "the app is not yet running or not debuggable");
        }
      }
      catch (InterruptedException ex) {
        LOG.warn(ex);
        return new DisableMessage(DisableMessage.DisableMode.DISABLED, "update interrupted", "its status update was interrupted");
      }
      catch (ExecutionException ex) {
        LOG.warn(ex);
        return new DisableMessage(DisableMessage.DisableMode.DISABLED, "unknown device API level",
                                  "its API level could not be determined");
      }
      catch (Exception ex) {
        LOG.warn(ex);
        return new DisableMessage(
          DisableMessage.DisableMode.DISABLED, "unexpected exception", "an unexpected exception was thrown: " + ex.toString());
      }
    }

    return null;
  }

  private static boolean isApplyChangesRelevant(@NotNull RunConfiguration runConfiguration) {
    if (runConfiguration instanceof RunConfigurationBase) {
      RunConfigurationBase configBase = (RunConfigurationBase) runConfiguration;
      configBase.putUserDataIfAbsent(SHOW_APPLY_CHANGES_UI, false); // This is needed to prevent a NPE if the boolean isn't set.
      //noinspection ConstantConditions
      return configBase.getUserData(SHOW_APPLY_CHANGES_UI);
    }

    return false;
  }

  /**
   * Check if there are any executors of the current {@link RunConfiguration} that is starting up. We should not swap when this is true.
   */
  private static boolean isExecutorStarting(@NotNull Project project, @NotNull RunConfiguration runConfiguration) {
    // Check if any executors are starting up (e.g. if the user JUST clicked on an executor, and deployment hasn't finished).
    Executor[] executors = ExecutorRegistry.getInstance().getRegisteredExecutors();
    for (Executor executor : executors) {
      ProgramRunner programRunner = ProgramRunner.getRunner(executor.getId(), runConfiguration);
      if (programRunner == null) {
        continue;
      }
      if (ExecutorRegistry.getInstance().isStarting(project, executor.getId(), programRunner.getRunnerId())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      LOG.warn(myName + " action performed with no project");
      return;
    }

    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
    if (settings == null) {
      LOG.warn(myName + " action could not locate current run config settings");
      return;
    }

    ProcessHandler handler = findRunningProcessHandler(project, settings.getConfiguration());
    Executor executor = handler == null ? null : getExecutor(handler, DefaultRunExecutor.getRunExecutorInstance());
    if (executor == null) {
      LOG.warn(myName + " action could not identify executor of existing running application");
      return;
    }

    ExecutionEnvironmentBuilder builder = ExecutionEnvironmentBuilder.create(executor, settings.getConfiguration());
    ExecutionEnvironment env = builder.activeTarget().dataContext(e.getDataContext()).build();

    env.putCopyableUserData(myKey, true);
    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  @Nullable
  protected static ProcessHandler findRunningProcessHandler(@NotNull Project project, @NotNull RunConfiguration runConfiguration) {
    for (ProcessHandler handler : ExecutionManager.getInstance(project).getRunningProcesses()) {
      SwappableProcessHandler extension = handler.getCopyableUserData(SwappableProcessHandler.EXTENSION_KEY);
      if (extension == null) {
        continue; // We may have a non-swappable process running.
      }

      if (extension.isExecutedWith(runConfiguration, ExecutionTargetManager.getActiveTarget(project)) &&
          handler.isStartNotified() &&
          !handler.isProcessTerminating() &&
          !handler.isProcessTerminated()) {
        return handler;
      }
    }
    return null;
  }

  @Nullable
  private static Executor getExecutor(@NotNull ProcessHandler processHandler, @Nullable Executor defaultExecutor) {
    if (processHandler instanceof RemoteDebugProcessHandler) {
      // Special case for remote debugger.
      return DefaultDebugExecutor.getDebugExecutorInstance();
    }

    SwappableProcessHandler extension = processHandler.getCopyableUserData(SwappableProcessHandler.EXTENSION_KEY);
    return processHandler.isProcessTerminated() || processHandler.isProcessTerminating() || extension == null
           ? defaultExecutor
           : extension.getExecutor();
  }

  protected void disableAction(@NotNull Presentation presentation, @NotNull DisableMessage disableMessage) {
    presentation.setVisible(disableMessage.myDisableMode != DisableMessage.DisableMode.INVISIBLE);
    presentation.setEnabled(false);
    presentation.setText(String.format("%s (disabled: %s)", myName, disableMessage.myTooltip));
    presentation.setDescription(String.format("%s is disabled for this device because %s.", myName, disableMessage.myDescription));
  }

  public static final class DisableMessage {
    public enum DisableMode {
      INVISIBLE,
      DISABLED
    }

    @NotNull
    private final DisableMode myDisableMode;
    @NotNull
    private final String myTooltip;
    @NotNull
    private final String myDescription;

    public DisableMessage(@NotNull DisableMode disableMode, @NotNull String tooltip, @NotNull String description) {
      myDisableMode = disableMode;
      myTooltip = tooltip;
      myDescription = description;
    }

    @NotNull
    public String getDescription() {
      return myDescription;
    }
  }
}
