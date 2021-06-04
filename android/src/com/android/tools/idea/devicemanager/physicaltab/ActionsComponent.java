/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.tools.idea.explorer.DeviceExplorerToolWindowFactory;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.scale.JBUIScale;
import java.awt.Component;
import java.util.function.BiConsumer;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.Icon;
import javax.swing.JButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ActionsComponent extends JBPanel<ActionsComponent> {
  private final @Nullable Project myProject;
  private final @Nullable PhysicalDeviceTableModel myModel;
  private final @NotNull BiConsumer<@NotNull Project, @NotNull String> myOpenAndShowDevice;
  private final @NotNull NewEditDeviceNameDialog myNewEditDeviceNameDialog;

  private final @NotNull AbstractButton myActivateDeviceFileExplorerWindowButton;
  private final @NotNull AbstractButton myEditDeviceNameButton;
  private final @NotNull Component myMoreButton;

  private @Nullable PhysicalDevice myDevice;

  ActionsComponent(@Nullable Project project, @Nullable PhysicalDeviceTableModel model) {
    this(project, model, DeviceExplorerToolWindowFactory::openAndShowDevice, EditDeviceNameDialog::new);
  }

  @VisibleForTesting
  ActionsComponent(@Nullable Project project,
                   @Nullable PhysicalDeviceTableModel model,
                   @NotNull BiConsumer<@NotNull Project, @NotNull String> openAndShowDevice,
                   @NotNull NewEditDeviceNameDialog newEditDeviceNameDialog) {
    super(null);

    myProject = project;
    myModel = model;
    myOpenAndShowDevice = openAndShowDevice;
    myNewEditDeviceNameDialog = newEditDeviceNameDialog;

    myActivateDeviceFileExplorerWindowButton = newJButton(AllIcons.General.OpenDiskHover, this::activateDeviceFileExplorerWindow);
    myEditDeviceNameButton = newJButton(AllIcons.Actions.Edit, this::editDeviceName);

    myMoreButton = newJButton(AllIcons.Actions.More, () -> {
    });

    setLayout();
  }

  private static @NotNull AbstractButton newJButton(@NotNull Icon icon, @NotNull Runnable runnable) {
    AbstractButton button = new JButton(icon);

    button.setBorderPainted(false);
    button.setContentAreaFilled(false);
    button.addActionListener(event -> runnable.run());

    return button;
  }

  private void activateDeviceFileExplorerWindow() {
    if (myProject == null) {
      return;
    }

    if (myDevice == null) {
      return;
    }

    if (!myDevice.isOnline()) {
      return;
    }

    myOpenAndShowDevice.accept(myProject, myDevice.getKey().toString());
  }

  private void editDeviceName() {
    assert myDevice != null;
    EditDeviceNameDialog dialog = myNewEditDeviceNameDialog.apply(myProject, myDevice.getNameOverride(), myDevice.getName());

    if (!dialog.showAndGet()) {
      return;
    }

    assert myModel != null;
    myModel.setNameOverride(myDevice.getKey(), dialog.getNameOverride());
  }

  private void setLayout() {
    GroupLayout layout = new GroupLayout(this);
    int size = JBUIScale.scale(22);

    Group horizontalGroup = layout.createSequentialGroup()
      .addGap(0, 0, Short.MAX_VALUE)
      .addComponent(myActivateDeviceFileExplorerWindowButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE)
      // .addComponent(myEditDeviceNameButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE)
      .addComponent(myMoreButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE);

    Group verticalGroup = layout.createSequentialGroup()
      .addGap(0, 0, Short.MAX_VALUE)
      .addGroup(layout.createParallelGroup()
                  .addComponent(myActivateDeviceFileExplorerWindowButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE)
                  // .addComponent(myEditDeviceNameButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE)
                  .addComponent(myMoreButton, GroupLayout.PREFERRED_SIZE, size, GroupLayout.PREFERRED_SIZE))
      .addGap(0, 0, Short.MAX_VALUE);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }

  @VisibleForTesting
  @NotNull AbstractButton getActivateDeviceFileExplorerWindowButton() {
    return myActivateDeviceFileExplorerWindowButton;
  }

  @VisibleForTesting
  @NotNull AbstractButton getEditDeviceNameButton() {
    return myEditDeviceNameButton;
  }

  @VisibleForTesting
  @NotNull PhysicalDevice getDevice() {
    assert myDevice != null;
    return myDevice;
  }

  void setDevice(@NotNull PhysicalDevice device) {
    myDevice = device;
  }
}
