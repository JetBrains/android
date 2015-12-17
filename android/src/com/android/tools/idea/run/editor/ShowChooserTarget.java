/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.run.editor;

import com.android.tools.idea.run.*;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;

public class ShowChooserTarget extends DeployTarget<ShowChooserTarget.State> {
  private DeployTargetPickerDialog.Result myResult;

  // Note: we only maintain the state that is persisted along with the run configuration here.
  // Any other state that is necessary for the dialog itself should be maintained separately, possibly indexed by the run configuration
  // context in which getTarget() is invoked.
  public static final class State extends DeployTargetState {
    public boolean USE_LAST_SELECTED_DEVICE;
  }

  @NotNull
  @Override
  public String getId() {
    return TargetSelectionMode.SHOW_DIALOG.name();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Show Device Chooser Dialog";
  }

  @NotNull
  @Override
  public State createState() {
    return new State();
  }

  @Override
  public boolean requiresRuntimePrompt(@NotNull State deployTargetState) {
    return true;
  }

  @Override
  public boolean showPrompt(Executor executor,
                            @NotNull ExecutionEnvironment env,
                            AndroidFacet facet,
                            DeviceCount deviceCount,
                            boolean androidTests,
                            @NotNull Map<String, DeployTargetState> deployTargetStates,
                            int runConfigId,
                            ProcessHandlerConsolePrinter printer) {
    /**
     * 1. Figure out if we need to show the dialog
     *    If not, then set the state and move on
     * 2. Show the dialog, get the user choice: we are going to do one of:
     *    a. use an existing device or avd, in which case it is going to be DeviceTarget
     *    b. will use a custom launcher.
     *    Save that state and reuse in the the subsequent calls to hasCustomRunProfileState() or getTarget()
     */

    State showChooserState = (State)deployTargetStates.get(getId());

    // TODO: see if there is saved state for this run config context, and that selection is still valid
    // if so, set the current state from the previous selection. Currently, this is handled by the ManualTargetChooser
    //if (showChooserState.USE_LAST_SELECTED_DEVICE) {
    //}

    // If we are not showing any custom run/profile states, then show the old style device chooser
    List<DeployTarget> applicableTargets = getTargetsProvidingRunProfileState(executor, androidTests);
    if (applicableTargets.isEmpty()) {
      DeviceTarget deviceTarget = new ManualTargetChooser(showChooserState, facet, runConfigId)
        .getTarget(printer, deviceCount, executor instanceof DefaultDebugExecutor);
      if (deviceTarget == null) {
        return false;
      }

      myResult = DeployTargetPickerDialog.Result.create(deviceTarget);
      return true;
    }

    // show the dialog and get the state
    DeployTargetPickerDialog dialog = new DeployTargetPickerDialog(runConfigId, facet, applicableTargets, deployTargetStates, printer);
    if (dialog.showAndGet()) {
      myResult = dialog.getResult();
      return true;
    }
    else {
      return false;
    }
  }

  @NotNull
  private static List<DeployTarget> getTargetsProvidingRunProfileState(@NotNull Executor executor, boolean androidTests) {
    List<DeployTarget> targets = Lists.newArrayList();

    for (DeployTarget target : DeployTarget.getDeployTargets()) {
      if (target.showInDevicePicker() && target.isApplicable(androidTests) && target.hasCustomRunProfileState(executor)) {
        targets.add(target);
      }
    }

    return targets;
  }

  @Override
  public DeployTargetConfigurable createConfigurable(@NotNull Project project,
                                                     @NotNull Disposable parentDisposable,
                                                     @NotNull DeployTargetConfigurableContext context) {
    return new ShowChooserConfigurable();
  }

  @Override
  public boolean hasCustomRunProfileState(@NotNull Executor executor) {
    return myResult != null && myResult.hasRunProfile();
  }

  @Override
  public RunProfileState getRunProfileState(@NotNull Executor executor, @NotNull ExecutionEnvironment env, @NotNull State state)
    throws ExecutionException {
    return myResult.getRunProfileState(executor, env, state);
  }

  @Nullable
  @Override
  public DeviceTarget getTarget(@NotNull State state,
                                @NotNull AndroidFacet facet,
                                @NotNull DeviceCount deviceCount,
                                boolean debug,
                                int runConfigId,
                                @NotNull ConsolePrinter printer) {
    return myResult.getDeviceTarget();
  }

  private static class ShowChooserConfigurable implements DeployTargetConfigurable<State> {
    private final JBCheckBox myCheckbox;

    public ShowChooserConfigurable() {
      myCheckbox = new JBCheckBox("Use same device for future launches");
    }

    @Nullable
    @Override
    public JComponent createComponent() {
      return myCheckbox;
    }

    @Override
    public void resetFrom(@NotNull State state, int uniqueID) {
      myCheckbox.setSelected(state.USE_LAST_SELECTED_DEVICE);
    }

    @Override
    public void applyTo(@NotNull State state, int uniqueID) {
      state.USE_LAST_SELECTED_DEVICE = myCheckbox.isSelected();
    }
  }
}
