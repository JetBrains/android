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
package com.android.tools.idea.run.deployment;

import com.android.ddmlib.IDevice;
import com.intellij.execution.DefaultExecutionTarget;
import com.intellij.execution.ExecutionTarget;
import com.intellij.execution.ExecutionTargetProvider;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import java.util.Collections;
import java.util.List;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeviceAndSnapshotExecutionTargetProvider extends ExecutionTargetProvider {
  @NotNull
  @Override
  public List<ExecutionTarget> getTargets(@NotNull Project project, @NotNull RunnerAndConfigurationSettings configuration) {
    ActionManager manager = ActionManager.getInstance();
    DeviceAndSnapshotComboBoxAction action = (DeviceAndSnapshotComboBoxAction)manager.getAction("DeviceAndSnapshotComboBox");

    // We always return an ExecutionTarget as long as we have valid config and package names.
    // This is because we don't want to maintain listener states with ddmlib, and we don't
    // need to keep track of online/offline states, and we let things that need the states
    // to query on their own.
    Device device = action.getSelectedDevice(project);
    if (device != null) {
      return Collections.singletonList(new Target(device));
    }

    return Collections.singletonList(DefaultExecutionTarget.INSTANCE);
  }

  static class Target extends AndroidExecutionTarget {
    @NotNull private final Device myDevice;

    private Target(@NotNull Device device) {
      myDevice = device;
    }

    @NotNull
    @Override
    public String getId() {
      return myDevice.getKey().toString();
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return myDevice.getName();
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return myDevice.getIcon();
    }

    @Override
    public boolean canRun(@NotNull RunConfiguration configuration) {
      return true;
    }

    @Override
    public boolean isApplicationRunning(@NotNull String packageName) {
      return myDevice.isRunning(packageName);
    }

    @Nullable
    @Override
    public IDevice getIDevice() {
      return myDevice.getDdmlibDevice();
    }

    @NotNull
    Device getDevice() {
      return myDevice;
    }
  }
}
