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
import com.google.common.collect.Sets;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.annotations.OptionTag;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;
import com.intellij.util.xmlb.annotations.XCollection.Style;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
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
      return Optional.of(devices.get(0).getDefaultTarget());
    }

    Target target;

    try {
      target = state.targetSelectedWithDropDown.asTarget();
    }
    catch (DevicesSelectedServiceException exception) {
      Logger.getInstance(DevicesSelectedService.class).warn(exception);
      return Optional.of(devices.get(0).getDefaultTarget());
    }

    Optional<Device> optionalSelectedDevice = devices.stream()
      .filter(target::matches)
      .findFirst();

    if (!optionalSelectedDevice.isPresent()) {
      return Optional.of(devices.get(0).getDefaultTarget());
    }

    Optional<Device> optionalConnectedDevice = devices.stream()
      .filter(Device::isConnected)
      .findFirst();

    if (!optionalConnectedDevice.isPresent()) {
      return Optional.of(target);
    }

    Device connectedDevice = optionalConnectedDevice.get();

    assert state.timeTargetWasSelectedWithDropDown != null;

    Instant connectionTime = connectedDevice.getConnectionTime();
    assert connectionTime != null;

    if (state.timeTargetWasSelectedWithDropDown.isBefore(connectionTime)) {
      return Optional.of(connectedDevice.getDefaultTarget());
    }

    return Optional.of(target);
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
    try {
      Collection<TargetState> targetStates = myPersistentStateComponent.getState().targetsSelectedWithDialog;
      Set<Target> targets = Sets.newHashSetWithExpectedSize(targetStates.size());

      for (TargetState targetState : targetStates) {
        targets.add(targetState.asTarget());
      }

      return targets;
    }
    catch (DevicesSelectedServiceException exception) {
      Logger.getInstance(DevicesSelectedService.class).warn(exception);
      return Collections.emptySet();
    }
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
    @OptionTag(tag = "type", nameAttribute = "")
    public @Nullable TargetType type;

    @OptionTag(tag = "deviceKey", nameAttribute = "")
    public @Nullable KeyState deviceKey;

    @OptionTag(tag = "snapshotKey", nameAttribute = "", converter = PathConverter.class)
    public @Nullable Path snapshotKey;

    @SuppressWarnings("unused")
    private TargetState() {
    }

    private TargetState(@NotNull Target target) {
      if (target instanceof RunningDeviceTarget) {
        type = TargetType.RUNNING_DEVICE_TARGET;
      }
      else if (target instanceof ColdBootTarget) {
        type = TargetType.COLD_BOOT_TARGET;
      }
      else if (target instanceof QuickBootTarget) {
        type = TargetType.QUICK_BOOT_TARGET;
      }
      else if (target instanceof BootWithSnapshotTarget) {
        type = TargetType.BOOT_WITH_SNAPSHOT_TARGET;
        snapshotKey = ((BootWithSnapshotTarget)target).getSnapshotKey();
      }
      else {
        assert false : target;
      }

      deviceKey = new KeyState(target.getDeviceKey());
    }

    private @NotNull Target asTarget() throws DevicesSelectedServiceException {
      if (type == null) {
        throw new DevicesSelectedServiceException();
      }

      if (deviceKey == null) {
        throw new DevicesSelectedServiceException();
      }

      switch (type) {
        case RUNNING_DEVICE_TARGET:
          return new RunningDeviceTarget(deviceKey.asKey());
        case COLD_BOOT_TARGET:
          return new ColdBootTarget(deviceKey.asKey());
        case QUICK_BOOT_TARGET:
          return new QuickBootTarget(deviceKey.asKey());
        case BOOT_WITH_SNAPSHOT_TARGET:
          assert snapshotKey != null;
          return new BootWithSnapshotTarget(deviceKey.asKey(), snapshotKey);
        default:
          throw new DevicesSelectedServiceException(type.toString());
      }
    }

    @Override
    public int hashCode() {
      int hashCode = Objects.hashCode(type);

      hashCode = 31 * hashCode + Objects.hashCode(deviceKey);
      hashCode = 31 * hashCode + Objects.hashCode(snapshotKey);

      return hashCode;
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (!(object instanceof TargetState)) {
        return false;
      }

      TargetState target = (TargetState)object;

      return Objects.equals(type, target.type) &&
             Objects.equals(deviceKey, target.deviceKey) &&
             Objects.equals(snapshotKey, target.snapshotKey);
    }
  }

  private enum TargetType {RUNNING_DEVICE_TARGET, COLD_BOOT_TARGET, QUICK_BOOT_TARGET, BOOT_WITH_SNAPSHOT_TARGET}

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

    private @NotNull Key asKey() throws DevicesSelectedServiceException {
      if (type == null) {
        throw new DevicesSelectedServiceException();
      }

      if (value == null) {
        throw new DevicesSelectedServiceException();
      }

      switch (type) {
        case VIRTUAL_DEVICE_PATH:
          return new VirtualDevicePath(value);
        case VIRTUAL_DEVICE_NAME:
          return new VirtualDeviceName(value);
        case SERIAL_NUMBER:
          return new SerialNumber(value);
        default:
          throw new DevicesSelectedServiceException(type.toString());
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

  private static final class PathConverter extends Converter<Path> {
    private final @NotNull FileSystem myFileSystem = FileSystems.getDefault();

    @Override
    public @NotNull Path fromString(@NotNull String string) {
      return myFileSystem.getPath(string);
    }

    @Override
    public @NotNull String toString(@NotNull Path path) {
      return path.toString();
    }
  }

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
