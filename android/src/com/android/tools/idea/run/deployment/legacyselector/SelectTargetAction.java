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
package com.android.tools.idea.run.deployment.legacyselector;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An item in the {@link SnapshotActionGroup submenu} for a virtual device. The {@link Target target} determines if an available virtual
 * device will be cold booted, quick booted, or booted with a snapshot.
 */
final class SelectTargetAction extends AnAction {
  private final @NotNull Target myTarget;
  private final @NotNull Device myDevice;
  private final @NotNull DeviceAndSnapshotComboBoxAction myComboBoxAction;

  SelectTargetAction(@NotNull Target target, @NotNull Device device, @NotNull DeviceAndSnapshotComboBoxAction comboBoxAction) {
    myTarget = target;
    myDevice = device;
    myComboBoxAction = comboBoxAction;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    event.getPresentation().setText(myTarget.getText(myDevice), false);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    myComboBoxAction.setTargetSelectedWithComboBox(Objects.requireNonNull(event.getProject()), myTarget);
  }

  @Override
  public int hashCode() {
    int hashCode = myTarget.hashCode();

    hashCode = 31 * hashCode + myDevice.hashCode();
    hashCode = 31 * hashCode + myComboBoxAction.hashCode();

    return hashCode;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof SelectTargetAction)) {
      return false;
    }

    SelectTargetAction action = (SelectTargetAction)object;
    return myTarget.equals(action.myTarget) && myDevice.equals(action.myDevice) && myComboBoxAction.equals(action.myComboBoxAction);
  }
}
