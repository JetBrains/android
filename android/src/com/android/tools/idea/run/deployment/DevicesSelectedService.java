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
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.util.xmlb.annotations.XCollection.Style;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A project scoped service that persists the targets selected with the drop down or the Select Multiple Devices dialog
 */
final class DevicesSelectedService {
  private final @NotNull PersistentStateComponent myPersistentStateComponent;

  @NotNull
  private final Clock myClock;

  private final @NotNull BooleanSupplier myRunOnMultipleDevicesActionEnabledGet;

  @SuppressWarnings("unused")
  private DevicesSelectedService(@NotNull Project project) {
    this(project.getService(PersistentStateComponent.class),
         Clock.systemDefaultZone(),
         StudioFlags.RUN_ON_MULTIPLE_DEVICES_ACTION_ENABLED::get);
  }

  @VisibleForTesting
  @NonInjectable
  DevicesSelectedService(@NotNull PersistentStateComponent persistentStateComponent,
                         @NotNull Clock clock,
                         @NotNull BooleanSupplier runOnMultipleDevicesActionEnabledGet) {
    myPersistentStateComponent = persistentStateComponent;
    myClock = clock;
    myRunOnMultipleDevicesActionEnabledGet = runOnMultipleDevicesActionEnabledGet;
  }

  @NotNull
  static DevicesSelectedService getInstance(@NotNull Project project) {
    return project.getService(DevicesSelectedService.class);
  }

  @NotNull Optional<@NotNull Target> getTargetSelectedWithComboBox(@NotNull List<@NotNull Device> devices) {
    if (devices.isEmpty()) {
      return Optional.empty();
    }

    State state = myPersistentStateComponent.getState();

    if (state.targetSelectedWithDropDown == null) {
      return Optional.of(new Target(devices.get(0).getKey()));
    }

    assert state.targetSelectedWithDropDown.deviceKey != null;
    Key key = state.targetSelectedWithDropDown.deviceKey.asKey();

    Optional<Device> optionalSelectedDevice = devices.stream()
      .filter(device -> device.matches(key))
      .findFirst();

    if (!optionalSelectedDevice.isPresent()) {
      return Optional.of(new Target(devices.get(0).getKey()));
    }

    Device selectedDevice = optionalSelectedDevice.get();

    Optional<Device> optionalConnectedDevice = devices.stream()
      .filter(Device::isConnected)
      .findFirst();

    if (!optionalConnectedDevice.isPresent()) {
      return Optional.of(new Target(selectedDevice.getKey()));
    }

    Device connectedDevice = optionalConnectedDevice.get();

    assert state.timeTargetWasSelectedWithDropDown != null;

    Instant connectionTime = connectedDevice.getConnectionTime();
    assert connectionTime != null;

    if (state.timeTargetWasSelectedWithDropDown.isBefore(connectionTime)) {
      return Optional.of(new Target(connectedDevice.getKey()));
    }

    return Optional.of(new Target(selectedDevice.getKey()));
  }

  void setTargetSelectedWithComboBox(@Nullable Target targetSelectedWithComboBox) {
    State state = myPersistentStateComponent.getState();
    state.multipleDevicesSelectedInDropDown = false;

    if (targetSelectedWithComboBox == null) {
      state.timeTargetWasSelectedWithDropDown = null;
      state.targetSelectedWithDropDown = null;
    }
    else {
      state.targetSelectedWithDropDown = new TargetState(targetSelectedWithComboBox);
      state.timeTargetWasSelectedWithDropDown = myClock.instant();
    }
  }

  boolean isMultipleDevicesSelectedInComboBox() {
    return !myRunOnMultipleDevicesActionEnabledGet.getAsBoolean() &&
           myPersistentStateComponent.getState().multipleDevicesSelectedInDropDown;
  }

  void setMultipleDevicesSelectedInComboBox(boolean multipleDevicesSelectedInComboBox) {
    State state = myPersistentStateComponent.getState();

    state.timeTargetWasSelectedWithDropDown = null;
    state.targetSelectedWithDropDown = null;

    state.multipleDevicesSelectedInDropDown = multipleDevicesSelectedInComboBox;
  }

  boolean isDialogSelectionEmpty() {
    return myPersistentStateComponent.getState().targetsSelectedWithDialog.isEmpty();
  }

  @NotNull Set<@NotNull Target> getTargetsSelectedWithDialog() {
    return myPersistentStateComponent.getState().targetsSelectedWithDialog.stream()
      .map(TargetState::asTarget)
      .collect(Collectors.toSet());
  }

  void setTargetsSelectedWithDialog(@NotNull Set<@NotNull Target> targetsSelectedWithDialog) {
    myPersistentStateComponent.getState().targetsSelectedWithDialog = targetsSelectedWithDialog.stream()
      .map(TargetState::new)
      .collect(Collectors.toList());
  }

  @com.intellij.openapi.components.State(name = "deploymentTargetDropDown", storages = @Storage("deploymentTargetDropDown.xml"))
  @Service
  @VisibleForTesting
  static final class PersistentStateComponent implements com.intellij.openapi.components.PersistentStateComponent<State> {
    private @NotNull State myState = new State();

    @Override
    public @NotNull State getState() {
      return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
      myState = state;
    }
  }

  private static final class State {
    @OptionTag(tag = "targetSelectedWithDropDown", nameAttribute = "")
    public @Nullable TargetState targetSelectedWithDropDown;

    @OptionTag(tag = "timeTargetWasSelectedWithDropDown", nameAttribute = "", converter = InstantConverter.class)
    public @Nullable Instant timeTargetWasSelectedWithDropDown;

    @OptionTag(tag = "multipleDevicesSelectedInDropDown", nameAttribute = "")
    public boolean multipleDevicesSelectedInDropDown;

    @XCollection(style = Style.v2)
    public @NotNull Collection<@NotNull TargetState> targetsSelectedWithDialog = Collections.emptyList();

    @Override
    public int hashCode() {
      int hashCode = Objects.hashCode(targetSelectedWithDropDown);

      hashCode = 31 * hashCode + Objects.hashCode(timeTargetWasSelectedWithDropDown);
      hashCode = 31 * hashCode + Boolean.hashCode(multipleDevicesSelectedInDropDown);
      hashCode = 31 * hashCode + targetsSelectedWithDialog.hashCode();

      return hashCode;
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (!(object instanceof State)) {
        return false;
      }

      State state = (State)object;

      return Objects.equals(targetSelectedWithDropDown, state.targetSelectedWithDropDown) &&
             Objects.equals(timeTargetWasSelectedWithDropDown, state.timeTargetWasSelectedWithDropDown) &&
             multipleDevicesSelectedInDropDown == state.multipleDevicesSelectedInDropDown &&
             targetsSelectedWithDialog.equals(state.targetsSelectedWithDialog);
    }
  }

  @Tag("Target")
  private static final class TargetState {
    @OptionTag(tag = "deviceKey", nameAttribute = "")
    public @Nullable KeyState deviceKey;

    @SuppressWarnings("unused")
    private TargetState() {
    }

    private TargetState(@NotNull Target target) {
      deviceKey = new KeyState(target.getDeviceKey());
    }

    private @NotNull Target asTarget() {
      assert deviceKey != null;
      return new Target(deviceKey.asKey());
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(deviceKey);
    }

    @Override
    public boolean equals(@Nullable Object object) {
      return object instanceof TargetState && Objects.equals(deviceKey, ((TargetState)object).deviceKey);
    }
  }

  @Tag("Key")
  private static final class KeyState {
    @OptionTag(tag = "type", nameAttribute = "")
    public @Nullable KeyType type;

    @OptionTag(tag = "value", nameAttribute = "")
    public @Nullable String value;

    @SuppressWarnings("unused")
    private KeyState() {
    }

    private KeyState(@NotNull Key key) {
      if (key instanceof VirtualDevicePath) {
        type = KeyType.VIRTUAL_DEVICE_PATH;
      }
      else if (key instanceof VirtualDeviceName) {
        type = KeyType.VIRTUAL_DEVICE_NAME;
      }
      else if (key instanceof SerialNumber) {
        type = KeyType.SERIAL_NUMBER;
      }
      else {
        assert false : key;
      }

      value = key.toString();
    }

    private @NotNull Key asKey() {
      assert type != null;
      assert value != null;

      switch (type) {
        case VIRTUAL_DEVICE_PATH:
          return new VirtualDevicePath(value);
        case VIRTUAL_DEVICE_NAME:
          return new VirtualDeviceName(value);
        case SERIAL_NUMBER:
          return new SerialNumber(value);
        default:
          throw new AssertionError(type);
      }
    }

    @Override
    public int hashCode() {
      return 31 * Objects.hashCode(type) + Objects.hashCode(value);
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (!(object instanceof KeyState)) {
        return false;
      }

      KeyState key = (KeyState)object;
      return Objects.equals(type, key.type) && Objects.equals(value, key.value);
    }
  }

  private enum KeyType {VIRTUAL_DEVICE_PATH, VIRTUAL_DEVICE_NAME, SERIAL_NUMBER}

  private static final class InstantConverter extends Converter<Instant> {
    @Override
    public @NotNull Instant fromString(@NotNull String string) {
      return Instant.parse(string);
    }

    @Override
    public @NotNull String toString(@NotNull Instant instant) {
      return instant.toString();
    }
  }
}
