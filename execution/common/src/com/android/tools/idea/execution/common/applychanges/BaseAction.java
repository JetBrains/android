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

import static com.android.tools.idea.run.tasks.AbstractDeployTask.MIN_API_VERSION;
import static com.android.tools.idea.run.util.SwapInfo.SWAP_INFO_KEY;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.execution.common.AndroidExecutionTarget;
import com.android.tools.idea.execution.common.AppRunConfiguration;
import com.android.tools.idea.execution.common.UtilsKt;
import com.android.tools.idea.run.DeploymentApplicationService;
import com.android.tools.idea.run.util.SwapInfo;
import com.android.tools.idea.run.util.SwapInfo.SwapType;
import com.android.tools.idea.util.CommonAndroidUtil;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.Executor;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseAction extends AnAction {
  public static final Key<Boolean> SHOW_APPLY_CHANGES_UI = Key.create("android.deploy.ApplyChanges.ShowUI");
  private static final Logger LOG = Logger.getInstance(BaseAction.class);
  @NotNull
  protected final String myName;

  @NotNull
  protected final Icon myIcon;

  @NotNull
  private final SwapType mySwapType;

  @NotNull
  private final String myDescription;

  public BaseAction(@NotNull String id,
                    @NotNull String name,
                    @NotNull String acceleratorName,
                    @NotNull SwapType swapType,
                    @NotNull Icon icon,
                    @NotNull Shortcut shortcut,
                    @NotNull String description) {
    super(acceleratorName, description, icon);
    myName = name;
    mySwapType = swapType;
    myIcon = icon;
    myDescription = description;

    KeymapManager manager = KeymapManager.getInstance();
    if (manager != null) {
      final Keymap keymap = manager.getActiveKeymap();
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

  /**
   * Apply Changes UI is:
   * - Visible if it's relevant to the currently selected run configuration.  E.g. Apply Changes UI is irrelevant
   * if the run configuration isn't an android app run configuration or android test configuration.
   * - Visible and enabled if it's applicable to the current run configuration and the project is compatible.
   */
  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project == null || !CommonAndroidUtil.getInstance().isAndroidProject(project)) {
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

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      LOG.warn(myName + " action performed with no project");
      return;
    }

    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
    AndroidExecutionTarget selectedExecutionTarget = (AndroidExecutionTarget)ExecutionTargetManager.getActiveTarget(project);

    if (settings == null) {
      LOG.warn(myName + " action could not locate current run config settings");
      return;
    }

    final List<ProcessHandler> runningProcessHandlers =
      UtilsKt.getProcessHandlersForDevices(settings, project, selectedExecutionTarget.getRunningDevices().stream().toList());

    final List<Executor> executors = getRunningExecutorsOfDifferentType(project, runningProcessHandlers);

    Executor executor = executors.isEmpty() ? DefaultRunExecutor.getRunExecutorInstance() : executors.get(0);
    if (executor == null) {
      LOG.warn(myName + " action could not identify executor of existing running application");
      return;
    }

    ExecutionEnvironmentBuilder builder = null;
    try {
      builder = ExecutionEnvironmentBuilder.create(executor, settings);
    }
    catch (com.intellij.execution.ExecutionException ex) {
      LOG.error(myName + " ExecutionException " + ex.getMessage());
      return;
    }
    ExecutionEnvironment env = builder.activeTarget().dataContext(e.getDataContext()).build();

    env.putUserData(SWAP_INFO_KEY, new SwapInfo(mySwapType));
    ProgramRunnerUtil.executeConfiguration(env, false, true);
  }

  protected void disableAction(@NotNull Presentation presentation, @NotNull DisableMessage disableMessage) {
    if (!presentation.isVisible()) return;
    presentation.setVisible(disableMessage.myDisableMode != DisableMessage.DisableMode.INVISIBLE);
    presentation.setEnabled(false);
    presentation.setText(String.format("%s (disabled: %s)", myName, disableMessage.myTooltip));
    presentation.setDescription(String.format("%s is disabled for this device because %s.", myName, disableMessage.myDescription));
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
    try {
      selectedRunConfig.checkConfiguration();
    }
    catch (RuntimeConfigurationException e) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "configuration has errors", "selected configuration contains errors");
    }

    if (!(selectedRunConfig instanceof AppRunConfiguration) || ((AppRunConfiguration)selectedRunConfig).getAppId() == null) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "can't detect package name", "can't detect package name");
    }

    String packageName = ((AppRunConfiguration)selectedRunConfig).getAppId();

    ExecutionTarget selectedExecutionTarget = ExecutionTargetManager.getActiveTarget(project);
    if (!(selectedExecutionTarget instanceof AndroidExecutionTarget)) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "unsupported execution target", "unsupported execution target");
    }
    AndroidExecutionTarget androidExecutionTarget = (AndroidExecutionTarget)selectedExecutionTarget;

    final Collection<IDevice> devices = androidExecutionTarget.getRunningDevices();

    if (devices.isEmpty()) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "devices not connected", "the selected devices are not connected");
    }

    if (devices.stream().anyMatch(d -> Objects.equals(d.getState(), IDevice.DeviceState.UNAUTHORIZED))) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "device not authorized",
                                "the selected device is not authorized");
    }

    if (devices.stream().anyMatch(d -> !d.getVersion().isGreaterOrEqualThan(MIN_API_VERSION))) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "incompatible device API level",
                                "its API level is lower than 26");
    }

    if (devices.stream().allMatch(d -> DeploymentApplicationService.getInstance().findClient(d, packageName).isEmpty())) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "app not detected",
                                "the app is not yet running or not debuggable");
    }


    if (isExecutorStarting(project, selectedRunConfig)) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "building and/or launching",
                                "the selected configuration is currently building and/or launching");
    }

    final List<ProcessHandler> runningProcessHandlers =
      UtilsKt.getProcessHandlersForDevices(configSettings, project, devices.stream().toList());

    final List<Executor> executors = getRunningExecutorsOfDifferentType(project, runningProcessHandlers);

    if (executors.size() > 1) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "more than one executor is running",
                                "more than one type of executor is running configuration");
    }

    Executor executor = executors.isEmpty() ? DefaultRunExecutor.getRunExecutorInstance() : executors.get(0);

    if (ProgramRunner.getRunner(executor.getId(), selectedRunConfig) == null) {
      return new DisableMessage(DisableMessage.DisableMode.DISABLED, "no runner available",
                                "there are no Program Runners available to run the given configuration (perhaps project needs a sync?)");
    }

    return null;
  }

  private static boolean isApplyChangesRelevant(@NotNull RunConfiguration runConfiguration) {
    if (runConfiguration instanceof RunConfigurationBase) {
      RunConfigurationBase configBase = (RunConfigurationBase)runConfiguration;
      return configBase.putUserDataIfAbsent(SHOW_APPLY_CHANGES_UI, false); // This is needed to prevent a NPE if the boolean isn't set.
    }

    return false;
  }

  /**
   * Check if there are any executors of the current {@link RunConfiguration} that is starting up. We should not swap when this is true.
   */
  private static boolean isExecutorStarting(@NotNull Project project, @NotNull RunConfiguration runConfiguration) {
    // Check if any executors are starting up (e.g. if the user JUST clicked on an executor, and deployment hasn't finished).
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
      ProgramRunner<?> programRunner = ProgramRunner.getRunner(executor.getId(), runConfiguration);
      if (programRunner == null) {
        continue;
      }
      if (ExecutionManager.getInstance(project).isStarting(executor.getId(), programRunner.getRunnerId())) {
        return true;
      }
    }
    return false;
  }

  protected static List<Executor> getRunningExecutorsOfDifferentType(@NotNull Project project, List<ProcessHandler> processHandlers) {
    ExecutionManagerImpl executionManager = ExecutionManagerImpl.getInstance(project);
    List<RunContentDescriptor> runningDescriptors =
      executionManager.getRunningDescriptors(c -> true).stream().filter(d -> processHandlers.contains(d.getProcessHandler())).toList();
    return runningDescriptors.stream().flatMap(r -> ExecutionManagerImpl.getInstance(project).getExecutors(r).stream()).distinct().toList();
  }

  public static final class DisableMessage {
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

    public enum DisableMode {
      INVISIBLE,
      DISABLED
    }
  }
}
