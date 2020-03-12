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
package com.android.tools.idea.run.deployment;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.serviceContainer.NonInjectable;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

final class ModifyDeviceSetAction extends AnAction {
  @NotNull
  private final DeviceAndSnapshotComboBoxAction myComboBoxAction;

  @NotNull
  private final Function<Project, DialogWrapper> myNewModifyDeviceSetDialog;

  @NonInjectable
  ModifyDeviceSetAction(@NotNull DeviceAndSnapshotComboBoxAction comboBoxAction) {
    this(comboBoxAction, ModifyDeviceSetDialog::new);
  }

  @VisibleForTesting
  @NonInjectable
  ModifyDeviceSetAction(@NotNull DeviceAndSnapshotComboBoxAction comboBoxAction,
                        @NotNull Function<Project, DialogWrapper> newModifyDeviceSetDialog) {
    super("Modify Device Set...");

    myComboBoxAction = comboBoxAction;
    myNewModifyDeviceSetDialog = newModifyDeviceSetDialog;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    Project project = event.getProject();

    if (project == null) {
      return;
    }

    if (!myNewModifyDeviceSetDialog.apply(project).showAndGet()) {
      return;
    }

    myComboBoxAction.modifyDeviceSet(project);
  }
}
