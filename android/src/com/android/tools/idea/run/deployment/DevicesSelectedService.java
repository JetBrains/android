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

import com.android.tools.idea.flags.StudioFlags;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.containers.ContainerUtil;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A project scoped service that wraps the PropertiesComponent that persists the device keys selected with the combo box or the Modify
 * Device Set dialog. The actual point of this is for stubbing and verification in tests.
 */
final class DevicesSelectedService {
  @VisibleForTesting
  static final String DEVICE_KEY_SELECTED_WITH_COMBO_BOX = "DeviceAndSnapshotComboBoxAction.selectedDevice";

  @VisibleForTesting
  static final String TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX = "DeviceAndSnapshotComboBoxAction.selectionTime";

  @VisibleForTesting
  static final String MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX = "DeviceAndSnapshotComboBoxAction.multipleDevicesSelected";

  @VisibleForTesting
  static final String DEVICE_KEYS_SELECTED_WITH_DIALOG = "SelectDeploymentTargetsDialog.selectedDevices";

  @NotNull
  private final Project myProject;

  @NotNull
  private final Function<Project, PropertiesComponent> myPropertiesComponentGetInstance;

  @NotNull
  private final Clock myClock;

  private final @NotNull BooleanSupplier myRunOnMultipleDevicesActionEnabledGet;

  @SuppressWarnings("unused")
  private DevicesSelectedService(@NotNull Project project) {
    this(project, PropertiesComponent::getInstance, Clock.systemDefaultZone(), StudioFlags.RUN_ON_MULTIPLE_DEVICES_ACTION_ENABLED::get);
  }

  @VisibleForTesting
  @NonInjectable
  DevicesSelectedService(@NotNull Project project,
                         @NotNull Function<Project, PropertiesComponent> propertiesComponentGetInstance,
                         @NotNull Clock clock,
                         @NotNull BooleanSupplier runOnMultipleDevicesActionEnabledGet) {
    myProject = project;
    myPropertiesComponentGetInstance = propertiesComponentGetInstance;
    myClock = clock;
    myRunOnMultipleDevicesActionEnabledGet = runOnMultipleDevicesActionEnabledGet;
  }

  @NotNull
  static DevicesSelectedService getInstance(@NotNull Project project) {
    return project.getService(DevicesSelectedService.class);
  }

  @Nullable
  Device getDeviceSelectedWithComboBox(@NotNull List<Device> devices) {
    if (devices.isEmpty()) {
      return null;
    }

    String keyAsString = myPropertiesComponentGetInstance.apply(myProject).getValue(DEVICE_KEY_SELECTED_WITH_COMBO_BOX);

    if (keyAsString == null) {
      return devices.get(0);
    }

    Key key = Key.newKey(keyAsString);

    Optional<Device> optionalSelectedDevice = devices.stream()
      .filter(device -> device.matches(key))
      .findFirst();

    if (!optionalSelectedDevice.isPresent()) {
      return devices.get(0);
    }

    Device selectedDevice = optionalSelectedDevice.get();

    Optional<Device> optionalConnectedDevice = devices.stream()
      .filter(Device::isConnected)
      .findFirst();

    if (!optionalConnectedDevice.isPresent()) {
      return selectedDevice;
    }

    Device connectedDevice = optionalConnectedDevice.get();

    Instant connectionTime = connectedDevice.getConnectionTime();
    assert connectionTime != null : "connected device \"" + connectedDevice + "\" has a null connection time";

    if (getTimeDeviceWasSelectedWithComboBox(selectedDevice).isBefore(connectionTime)) {
      return connectedDevice;
    }

    return selectedDevice;
  }

  @NotNull
  private Instant getTimeDeviceWasSelectedWithComboBox(@NotNull Device device) {
    CharSequence time = myPropertiesComponentGetInstance.apply(myProject).getValue(TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX);

    if (time == null) {
      // I don't know why this happens
      Logger.getInstance(DevicesSelectedService.class).warn("selected device \"" + device + "\" has a null selection time string");

      return Instant.MIN;
    }

    return Instant.parse(time);
  }

  void setDeviceSelectedWithComboBox(@Nullable Device deviceSelectedWithComboBox) {
    PropertiesComponent properties = myPropertiesComponentGetInstance.apply(myProject);
    properties.unsetValue(MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX);

    if (deviceSelectedWithComboBox == null) {
      properties.unsetValue(TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX);
      properties.unsetValue(DEVICE_KEY_SELECTED_WITH_COMBO_BOX);
    }
    else {
      properties.setValue(DEVICE_KEY_SELECTED_WITH_COMBO_BOX, deviceSelectedWithComboBox.getKey().toString());
      properties.setValue(TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX, myClock.instant().toString());
    }
  }

  boolean isMultipleDevicesSelectedInComboBox() {
    return !myRunOnMultipleDevicesActionEnabledGet.getAsBoolean() &&
           myPropertiesComponentGetInstance.apply(myProject).getBoolean(MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX);
  }

  void setMultipleDevicesSelectedInComboBox(boolean multipleDevicesSelectedInComboBox) {
    PropertiesComponent properties = myPropertiesComponentGetInstance.apply(myProject);

    properties.unsetValue(TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX);
    properties.unsetValue(DEVICE_KEY_SELECTED_WITH_COMBO_BOX);

    if (!multipleDevicesSelectedInComboBox) {
      properties.unsetValue(MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX);
    }
    else {
      properties.setValue(MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX, true);
    }
  }

  boolean isDialogSelectionEmpty() {
    return !myPropertiesComponentGetInstance.apply(myProject).isValueSet(DEVICE_KEYS_SELECTED_WITH_DIALOG);
  }

  @NotNull
  List<Device> getDevicesSelectedWithDialog(@NotNull List<Device> devices) {
    Collection<Key> keys = getDeviceKeysSelectedWithDialog();
    return ContainerUtil.filter(devices, device -> device.hasKeyContainedBy(keys));
  }

  void setDevicesSelectedWithDialog(@NotNull List<Device> devicesSelectedWithDialog) {
    setDeviceKeysSelectedWithDialog(devicesSelectedWithDialog.stream().map(Device::getKey));
  }

  @NotNull
  Set<Key> getDeviceKeysSelectedWithDialog() {
    String[] keys = myPropertiesComponentGetInstance.apply(myProject).getValues(DEVICE_KEYS_SELECTED_WITH_DIALOG);

    if (keys == null) {
      return Collections.emptySet();
    }

    assert !Arrays.asList(keys).contains("") : Arrays.toString(keys);

    return Arrays.stream(keys)
      .map(Key::newKey)
      .collect(Collectors.toSet());
  }

  void setDeviceKeysSelectedWithDialog(@NotNull Set<Key> deviceKeysSelectedWithDialog) {
    setDeviceKeysSelectedWithDialog(deviceKeysSelectedWithDialog.stream());
  }

  private void setDeviceKeysSelectedWithDialog(@NotNull Stream<Key> stream) {
    String[] array = stream
      .map(Key::toString)
      .toArray(String[]::new);

    PropertiesComponent properties = myPropertiesComponentGetInstance.apply(myProject);

    if (array.length == 0) {
      properties.unsetValue(DEVICE_KEYS_SELECTED_WITH_DIALOG);
    }
    else {
      properties.setValues(DEVICE_KEYS_SELECTED_WITH_DIALOG, array);
    }
  }
}
