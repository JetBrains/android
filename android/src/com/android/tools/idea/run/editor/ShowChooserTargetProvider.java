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
import com.intellij.execution.Executor;
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

public class ShowChooserTargetProvider extends DeployTargetProvider<ShowChooserTargetProvider.State> {
  public static final String ID = TargetSelectionMode.SHOW_DIALOG.name();

  // Note: we only maintain the state that is persisted along with the run configuration here.
  // Any other state that is necessary for the dialog itself should be maintained separately, possibly indexed by the run configuration
  // context in which getDeployTarget() is invoked.
  public static final class State extends DeployTargetState {
    public boolean USE_LAST_SELECTED_DEVICE;
  }

  @NotNull
  @Override
  public String getId() {
    return ID;
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
  public boolean requiresRuntimePrompt() {
    return true;
  }

  @Override
  public DeployTarget showPrompt(@NotNull Executor executor,
                            @NotNull ExecutionEnvironment env,
                            @NotNull AndroidFacet facet,
                            @NotNull DeviceCount deviceCount,
                            boolean androidTests,
                            @NotNull Map<String, DeployTargetState> deployTargetStates,
                            int runConfigId,
                            @NotNull ProcessHandlerConsolePrinter printer) {
    State showChooserState = (State)deployTargetStates.get(getId());

    // If we are not showing any custom run/profile states, then show the old style device chooser
    List<DeployTargetProvider> applicableTargets = getTargetsProvidingRunProfileState(executor, androidTests);
    if (applicableTargets.isEmpty()) {
      DeviceFutures deviceFutures = new ManualTargetChooser(showChooserState, facet, runConfigId)
        .getDevices(printer, deviceCount, executor instanceof DefaultDebugExecutor);
      if (deviceFutures == null) {
        return null;
      }

      return new RealizedDeployTarget(null, null, deviceFutures);
    }

    Project project = facet.getModule().getProject();

    if (showChooserState.USE_LAST_SELECTED_DEVICE) {
      DeployTarget target = DevicePickerStateService.getInstance(project).getDeployTargetPickerResult(runConfigId);
      if (target != null) {
        return target;
      }
    }

    // show the dialog and get the state
    DeployTargetPickerDialog dialog =
      new DeployTargetPickerDialog(runConfigId, facet, deviceCount, applicableTargets, deployTargetStates, printer);
    if (dialog.showAndGet()) {
      DeployTarget result = dialog.getSelectedDeployTarget();
      DevicePickerStateService.getInstance(project)
        .setDeployPickerResult(runConfigId, showChooserState.USE_LAST_SELECTED_DEVICE ? result : null);
      return result;
    }
    else {
      return null;
    }
  }

  @NotNull
  private static List<DeployTargetProvider> getTargetsProvidingRunProfileState(@NotNull Executor executor, boolean androidTests) {
    List<DeployTargetProvider> targets = Lists.newArrayList();

    for (DeployTargetProvider target : DeployTargetProvider.getProviders()) {
      if (target.showInDevicePicker() && target.isApplicable(androidTests)) {
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
  public DeployTarget<State> getDeployTarget() {
    throw new IllegalStateException("The results from the device picker should have been used.");
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
