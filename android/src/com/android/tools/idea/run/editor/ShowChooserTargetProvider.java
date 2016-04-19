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

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

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
  @Nullable
  public DeployTarget showPrompt(@NotNull Executor executor,
                                 @NotNull ExecutionEnvironment env,
                                 @NotNull AndroidFacet facet,
                                 @NotNull DeviceCount deviceCount,
                                 boolean androidTests,
                                 @NotNull Map<String, DeployTargetState> deployTargetStates,
                                 int runConfigId,
                                 @NotNull LaunchCompatibilityChecker compatibilityChecker) {
    State showChooserState = (State)deployTargetStates.get(getId());
    Project project = facet.getModule().getProject();

    if (showChooserState.USE_LAST_SELECTED_DEVICE) {
      // If the last selection was a custom run profile state, then use that
      DeployTarget target = DevicePickerStateService.getInstance(project).getDeployTargetPickerResult(runConfigId);
      if (target != null && target.hasCustomRunProfileState(executor)) {
        return target;
      }

      // If the last selection was a device, we can't just use the saved deploy target, since the list of devices could be stale,
      // which would happen if the result was to launch an emulator. So we use the state of the devices instead
      Collection<IDevice> devices = ManualTargetChooser.getLastUsedDevices(project, runConfigId, deviceCount);
      if (!devices.isEmpty()) {
        return new RealizedDeployTarget(null, null, DeviceFutures.forDevices(devices));
      }
    }

    List<DeployTargetProvider> applicableTargets = getTargetsProvidingRunProfileState(executor, androidTests);

    // show the dialog and get the state
    DeployTargetPickerDialog dialog =
      new DeployTargetPickerDialog(runConfigId, facet, deviceCount, applicableTargets, deployTargetStates, compatibilityChecker);
    if (dialog.showAndGet()) {
      DeployTarget result = dialog.getSelectedDeployTarget();
      if (result == null) {
        return null;
      }

      if (showChooserState.USE_LAST_SELECTED_DEVICE) {
        DevicePickerStateService.getInstance(project)
          .setDeployPickerResult(runConfigId, result.hasCustomRunProfileState(executor) ? result : null);

        // TODO: we only save the running devices, which means that the AVD selection is lost if the AVD wasn't running
        // TODO: the getRunningDevices() returns an empty list right now if there is an AVD to be selected, which at least makes the dialog
        // show up again, but eventually, we need to be able to handle a transition from AVD -> running device
        DevicePickerStateService.getInstance(project)
          .setDevicesUsedInLaunch(runConfigId, getRunningDevices(dialog.getSelectedDevices()), ManualTargetChooser.getOnlineDevices(project));
      }

      return result;
    }
    else {
      return null;
    }
  }

  private static Set<IDevice> getRunningDevices(@NotNull List<AndroidDevice> selectedDevices) {
    Set<IDevice> result = Sets.newHashSet();

    for (AndroidDevice device : selectedDevices) {
      if (device instanceof ConnectedAndroidDevice) {
        result.add(((ConnectedAndroidDevice)device).getDevice());
      }
      else {
        return Collections.emptySet();
      }
    }

    return result;
  }

  @NotNull
  private static List<DeployTargetProvider> getTargetsProvidingRunProfileState(@NotNull Executor executor, boolean androidTests) {
    List<DeployTargetProvider> targets = Lists.newArrayList();

    for (DeployTargetProvider target : DeployTargetProvider.getProviders()) {
      if (target.showInDevicePicker(executor) && target.isApplicable(androidTests)) {
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
