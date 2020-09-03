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

import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import icons.StudioIcons;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Updater {
  @NotNull
  private final Project myProject;

  @NotNull
  private final Presentation myPresentation;

  @NotNull
  private final String myPlace;

  @NotNull
  private final DevicesSelectedService myDevicesSelectedService;

  @NotNull
  private final List<Device> myDevices;

  @Nullable
  private final RunnerAndConfigurationSettings myConfigurationAndSettings;

  private final boolean mySnapshotsEnabled;

  static final class Builder {
    @Nullable
    private Project myProject;

    @Nullable
    private Presentation myPresentation;

    @NotNull
    private String myPlace = ActionPlaces.MAIN_TOOLBAR;

    @Nullable
    private DevicesSelectedService myDevicesSelectedService;

    @NotNull
    private List<Device> myDevices = Collections.emptyList();

    @Nullable
    private RunnerAndConfigurationSettings myConfigurationAndSettings;

    private boolean mySnapshotsEnabled;

    @NotNull
    Builder setProject(@NotNull Project project) {
      myProject = project;
      return this;
    }

    @NotNull
    Builder setPresentation(@NotNull Presentation presentation) {
      myPresentation = presentation;
      return this;
    }

    @NotNull
    Builder setPlace(@NotNull String place) {
      myPlace = place;
      return this;
    }

    @NotNull
    Builder setDevicesSelectedService(@NotNull DevicesSelectedService devicesSelectedService) {
      myDevicesSelectedService = devicesSelectedService;
      return this;
    }

    @NotNull
    Builder setDevices(@NotNull List<Device> devices) {
      myDevices = devices;
      return this;
    }

    @NotNull
    Builder setConfigurationAndSettings(@Nullable RunnerAndConfigurationSettings configurationAndSettings) {
      myConfigurationAndSettings = configurationAndSettings;
      return this;
    }

    @NotNull
    Builder setSnapshotsEnabled(boolean snapshotsEnabled) {
      mySnapshotsEnabled = snapshotsEnabled;
      return this;
    }

    @NotNull
    Updater build() {
      return new Updater(this);
    }
  }

  private Updater(@NotNull Builder builder) {
    assert builder.myProject != null;
    myProject = builder.myProject;

    assert builder.myPresentation != null;
    myPresentation = builder.myPresentation;

    myPlace = builder.myPlace;

    assert builder.myDevicesSelectedService != null;
    myDevicesSelectedService = builder.myDevicesSelectedService;

    myDevices = builder.myDevices;

    myConfigurationAndSettings = builder.myConfigurationAndSettings;
    mySnapshotsEnabled = builder.mySnapshotsEnabled;
  }

  void update() {
    if (!AndroidUtils.hasAndroidFacets(myProject)) {
      myPresentation.setVisible(false);
      return;
    }

    myPresentation.setVisible(true);
    updateDependingOnConfiguration();

    switch (myPlace) {
      case ActionPlaces.MAIN_TOOLBAR:
      case ActionPlaces.NAVIGATION_BAR_TOOLBAR:
        if (myDevicesSelectedService.isMultipleDevicesSelectedInComboBox()) {
          updateInToolbarForMultipleDevices();
        }
        else {
          updateInToolbarForSingleDevice();
        }

        break;
      default:
        myPresentation.setIcon(null);
        myPresentation.setText("Select Device...");

        break;
    }
  }

  private void updateDependingOnConfiguration() {
    if (myConfigurationAndSettings == null) {
      myPresentation.setEnabled(false);
      myPresentation.setDescription("Add a run/debug configuration");

      return;
    }

    RunProfile configuration = myConfigurationAndSettings.getConfiguration();

    if (configuration instanceof AndroidRunConfiguration || configuration instanceof AndroidTestRunConfiguration) {
      myPresentation.setEnabled(true);
      myPresentation.setDescription((String)null);

      return;
    }

    if (configurationDeploysToLocalDevice()) {
      myPresentation.setEnabled(true);
      myPresentation.setDescription((String)null);

      return;
    }

    myPresentation.setEnabled(false);
    myPresentation.setDescription("Not applicable for the \"" + configuration.getName() + "\" configuration");
  }

  private boolean configurationDeploysToLocalDevice() {
    assert myConfigurationAndSettings != null;
    Object configuration = myConfigurationAndSettings.getConfiguration();

    if (!(configuration instanceof UserDataHolder)) {
      return false;
    }

    Boolean deploysToLocalDevice = ((UserDataHolder)configuration).getUserData(DeviceAndSnapshotComboBoxAction.DEPLOYS_TO_LOCAL_DEVICE);
    return deploysToLocalDevice != null && deploysToLocalDevice;
  }

  private void updateInToolbarForMultipleDevices() {
    Set<Key> selectedKeys = myDevicesSelectedService.getDeviceKeysSelectedWithDialog();

    Set<Key> keys = myDevices.stream()
      .map(Device::getKey)
      .collect(Collectors.toSet());

    if (selectedKeys.retainAll(keys)) {
      myDevicesSelectedService.setDeviceKeysSelectedWithDialog(selectedKeys);

      if (selectedKeys.isEmpty()) {
        myDevicesSelectedService.setMultipleDevicesSelectedInComboBox(false);
        myDevicesSelectedService.setDeviceSelectedWithComboBox(myDevicesSelectedService.getDeviceSelectedWithComboBox(myDevices));

        updateInToolbarForSingleDevice();
        return;
      }
    }

    myPresentation.setIcon(StudioIcons.DeviceExplorer.MULTIPLE_DEVICES);
    myPresentation.setText("Multiple Devices (" + selectedKeys.size() + ")");
  }

  private void updateInToolbarForSingleDevice() {
    if (myDevices.isEmpty()) {
      myPresentation.setIcon(null);
      myPresentation.setText("No Devices");

      return;
    }

    Device device = myDevicesSelectedService.getDeviceSelectedWithComboBox(myDevices);
    assert device != null;

    myPresentation.setIcon(device.getIcon());
    myPresentation.setText(getText(device), false);
  }

  @NotNull
  private String getText(@NotNull Device device) {
    Key key = Devices.containsAnotherDeviceWithSameName(myDevices, device) ? device.getKey() : null;
    Snapshot snapshot = mySnapshotsEnabled ? device.getSnapshot() : null;

    return Devices.getText(device, key, snapshot);
  }
}
