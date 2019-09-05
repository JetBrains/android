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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import java.util.Collection;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SelectDeviceAndSnapshotAction extends AnAction {
  private final DeviceAndSnapshotComboBoxAction myComboBoxAction;
  private final Project myProject;
  private final Device myDevice;
  private Snapshot mySnapshot;

  static final class Builder {
    private DeviceAndSnapshotComboBoxAction myComboBoxAction;
    private Project myProject;
    private Device myDevice;
    private Snapshot mySnapshot;

    @NotNull
    Builder setComboBoxAction(@NotNull DeviceAndSnapshotComboBoxAction comboBoxAction) {
      myComboBoxAction = comboBoxAction;
      return this;
    }

    @NotNull
    Builder setProject(@NotNull Project project) {
      myProject = project;
      return this;
    }

    @NotNull
    Builder setDevice(@NotNull Device device) {
      myDevice = device;
      return this;
    }

    @NotNull
    Builder setSnapshot(@Nullable Snapshot snapshot) {
      mySnapshot = snapshot;
      return this;
    }

    @NotNull
    SelectDeviceAndSnapshotAction build() {
      return new SelectDeviceAndSnapshotAction(this);
    }
  }

  private SelectDeviceAndSnapshotAction(@NotNull Builder builder) {
    configurePresentation(builder);

    myComboBoxAction = builder.myComboBoxAction;
    myProject = builder.myProject;
    myDevice = builder.myDevice;

    initSnapshot(builder);
  }

  private void configurePresentation(@NotNull Builder builder) {
    Presentation presentation = getTemplatePresentation();

    if (builder.mySnapshot != null) {
      presentation.setText(builder.mySnapshot.getDisplayName(), false);
      return;
    }

    presentation.setText(Devices.getText(builder.myDevice, builder.myComboBoxAction.getDevices(builder.myProject)), false);
    presentation.setIcon(builder.myDevice.getIcon());
  }

  private void initSnapshot(@NotNull Builder builder) {
    if (builder.mySnapshot != null) {
      mySnapshot = builder.mySnapshot;
      return;
    }

    Collection<Snapshot> snapshots = builder.myDevice.getSnapshots();

    if (snapshots.isEmpty() || !builder.myComboBoxAction.areSnapshotsEnabled()) {
      mySnapshot = null;
      return;
    }

    if (snapshots.equals(VirtualDevice.DEFAULT_SNAPSHOT_COLLECTION)) {
      mySnapshot = Snapshot.DEFAULT;
      return;
    }

    throw new IllegalArgumentException(builder.toString());
  }

  @NotNull
  @VisibleForTesting
  public Device getDevice() {
    return myDevice;
  }

  @Nullable
  @VisibleForTesting
  Snapshot getSnapshot() {
    return mySnapshot;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    myComboBoxAction.setSelectedDevice(myProject, myDevice);
    myComboBoxAction.setSelectedSnapshot(myProject, mySnapshot);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof SelectDeviceAndSnapshotAction)) {
      return false;
    }

    SelectDeviceAndSnapshotAction action = (SelectDeviceAndSnapshotAction)object;

    return myComboBoxAction.equals(action.myComboBoxAction) &&
           myProject.equals(action.myProject) &&
           myDevice.equals(action.myDevice) &&
           Objects.equals(mySnapshot, action.mySnapshot);
  }

  @Override
  public int hashCode() {
    int hashCode = myComboBoxAction.hashCode();

    hashCode = 31 * hashCode + myProject.hashCode();
    hashCode = 31 * hashCode + myDevice.hashCode();
    hashCode = 31 * hashCode + Objects.hashCode(mySnapshot);

    return hashCode;
  }
}
