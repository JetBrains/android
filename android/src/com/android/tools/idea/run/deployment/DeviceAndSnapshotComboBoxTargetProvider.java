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
import com.android.tools.idea.run.editor.DeployTarget;
import com.android.tools.idea.run.editor.DeployTargetConfigurable;
import com.android.tools.idea.run.editor.DeployTargetConfigurableContext;
import com.android.tools.idea.run.editor.DeployTargetProvider;
import com.android.tools.idea.run.editor.DeployTargetState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

final class DeviceAndSnapshotComboBoxTargetProvider extends DeployTargetProvider {
  private DeviceAndSnapshotComboBoxTargetProvider() {
  }

  @NotNull
  @Override
  public String getId() {
    return TargetSelectionMode.DEVICE_AND_SNAPSHOT_COMBO_BOX.name();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public DeployTargetState createState() {
    return new State();
  }

  private static final class State extends DeployTargetState {
  }

  @Override
  public DeployTargetConfigurable createConfigurable(@NotNull Project project,
                                                     @NotNull Disposable parent,
                                                     @NotNull DeployTargetConfigurableContext context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public DeployTarget getDeployTarget() {
    throw new UnsupportedOperationException();
  }
}
