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

import com.android.tools.idea.run.TargetSelectionMode;
import com.android.tools.idea.run.deployment.DeviceAndSnapshotComboBoxTargetProvider.State;
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetConfigurable;
import com.android.tools.idea.run.editor.DeployTargetConfigurableContext;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DeviceAndSnapshotComboBoxTargetProvider extends DeployTargetProvider<State> {
  @NotNull
  @Override
  public String getId() {
    return TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX.name();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "Use the device/snapshot drop down";
  }

  @NotNull
  @Override
  public State createState() {
    return new State();
  }

  public static final class State extends DeployTargetState {
  }

  @NotNull
  @Override
  public DeployTargetConfigurable<State> createConfigurable(@NotNull Project project,
                                                            @NotNull Disposable parent,
                                                            @NotNull DeployTargetConfigurableContext context) {
    return new Configurable();
  }

  private static final class Configurable implements DeployTargetConfigurable<State> {
    @Nullable
    @Override
    public JComponent createComponent() {
      return null;
    }

    @Override
    public void resetFrom(@NotNull State state, int id) {
    }

    @Override
    public void applyTo(@NotNull State state, int id) {
    }
  }

  @NotNull
  @Override
  public DeployTarget<State> getDeployTarget() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public DeployTarget<State> getDeployTarget(@NotNull Project project) {
    return new DeviceAndSnapshotComboBoxTarget(DeviceAndSnapshotComboBoxAction.getInstance().getSelectedDevices(project));
  }
}
