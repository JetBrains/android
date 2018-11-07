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

import com.android.tools.idea.run.DeviceCount;
import com.android.tools.idea.run.DeviceFutures;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

final class DeviceAndSnapshotComboBoxTarget implements DeployTarget {
  @Override
  public boolean hasCustomRunProfileState(@NotNull Executor executor) {
    return false;
  }

  @NotNull
  @Override
  public RunProfileState getRunProfileState(@NotNull Executor executor,
                                            @NotNull ExecutionEnvironment environment,
                                            @NotNull DeployTargetState state) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public DeviceFutures getDevices(@NotNull DeployTargetState state,
                                  @NotNull AndroidFacet facet,
                                  @NotNull DeviceCount count,
                                  boolean debug,
                                  int id) {
    ActionManager manager = ActionManager.getInstance();
    DeviceAndSnapshotComboBoxAction action = (DeviceAndSnapshotComboBoxAction)manager.getAction("DeviceAndSnapshotComboBox");
    Project project = facet.getModule().getProject();

    Device device = action.getSelectedDevice(project);
    assert device != null;

    return device.newDeviceFutures(project, action.getSelectedSnapshot());
  }
}
