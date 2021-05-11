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
import java.util.function.BiConsumer;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ActionsComponent extends JBPanel<ActionsComponent> {
  private final @Nullable Project myProject;
  private final @NotNull BiConsumer<@NotNull Project, @NotNull String> myOpenAndShowDevice;

  private @Nullable AbstractButton myDeviceFileExplorerButton;
  private @Nullable AbstractButton myMoreButton;

  private @Nullable PhysicalDevice myDevice;

  ActionsComponent(@Nullable Project project) {
    this(project, DeviceExplorerToolWindowFactory::openAndShowDevice);
  }

  @VisibleForTesting
  ActionsComponent(@Nullable Project project, @NotNull BiConsumer<@NotNull Project, @NotNull String> openAndShowDevice) {
    super(null);

    myProject = project;
    myOpenAndShowDevice = openAndShowDevice;

    initDeviceFileExplorerButton();
    initMoreButton();

    setLayout();
  }

  private void initDeviceFileExplorerButton() {
    myDeviceFileExplorerButton = new JButton(AllIcons.General.OpenDiskHover);

    myDeviceFileExplorerButton.setBorderPainted(false);
    myDeviceFileExplorerButton.setContentAreaFilled(false);

    myDeviceFileExplorerButton.addActionListener(event -> {
      // TODO(http://b/187856375) Does the Device File Explorer need to work from the Welcome to Android Studio window?
      if (myProject == null) {
        return;
      }

      if (myDevice == null) {
        return;
      }

      if (!myDevice.isOnline()) {
        return;
      }

      myOpenAndShowDevice.accept(myProject, myDevice.getSerialNumber());
    });
  }

  private void initMoreButton() {
    myMoreButton = new JButton(AllIcons.Actions.More);

    myMoreButton.setBorderPainted(false);
    myMoreButton.setContentAreaFilled(false);
  }

  private void setLayout() {
    GroupLayout layout = new GroupLayout(this);

    Group horizontalGroup = layout.createSequentialGroup()
      .addGap(0, 0, Short.MAX_VALUE)
      .addComponent(myDeviceFileExplorerButton, GroupLayout.PREFERRED_SIZE, JBUIScale.scale(22), GroupLayout.PREFERRED_SIZE)
      .addComponent(myMoreButton, GroupLayout.PREFERRED_SIZE, JBUIScale.scale(22), GroupLayout.PREFERRED_SIZE);

    Group verticalGroup = layout.createSequentialGroup()
      .addGap(0, 0, Short.MAX_VALUE)
      .addGroup(layout.createParallelGroup()
                  .addComponent(myDeviceFileExplorerButton, GroupLayout.PREFERRED_SIZE, JBUIScale.scale(22), GroupLayout.PREFERRED_SIZE)
                  .addComponent(myMoreButton, GroupLayout.PREFERRED_SIZE, JBUIScale.scale(22), GroupLayout.PREFERRED_SIZE))
      .addGap(0, 0, Short.MAX_VALUE);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    setLayout(layout);
  }

  @VisibleForTesting
  @NotNull AbstractButton getDeviceFileExplorerButton() {
    assert myDeviceFileExplorerButton != null;
    return myDeviceFileExplorerButton;
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
