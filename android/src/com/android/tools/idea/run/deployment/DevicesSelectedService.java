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
import com.android.tools.idea.util.xmlb.InstantConverter;
import com.google.common.annotations.VisibleForTesting;
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
import java.util.ArrayList;
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

    RunningDeviceTarget runningDeviceTarget = state.getRunningDeviceTargetSelectedWithDropDown();
    Target target = state.getTargetSelectedWithDropDown();

    target = new TargetsForReadingSupplier(devices, runningDeviceTarget, target).getDropDownTarget().orElse(null);

    if (target == null) {
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

    TargetsForWritingSupplier supplier = new TargetsForWritingSupplier(state.getTargetSelectedWithDropDown(), targetSelectedWithComboBox);

    state.setRunningDeviceTargetSelectedWithDropDown(supplier.getDropDownRunningDeviceTarget().orElse(null));
    state.setTargetSelectedWithDropDown(supplier.getDropDownTarget().orElse(null));

    state.timeTargetWasSelectedWithDropDown = targetSelectedWithComboBox == null ? null : myClock.instant();
  }

  boolean isMultipleDevicesSelectedInComboBox() {
    return !myRunOnMultipleDevicesActionEnabledGet.getAsBoolean() &&
           myPersistentStateComponent.getState().multipleDevicesSelectedInDropDown;
  }

  void setMultipleDevicesSelectedInComboBox(boolean multipleDevicesSelectedInComboBox) {
    myPersistentStateComponent.getState().multipleDevicesSelectedInDropDown = multipleDevicesSelectedInComboBox;
  }

  @NotNull Set<@NotNull Target> getTargetsSelectedWithDialog(@NotNull List<@NotNull Device> devices) {
    State state = myPersistentStateComponent.getState();

    Collection<RunningDeviceTarget> runningDeviceTargets = state.getRunningDeviceTargetsSelectedWithDialog();
    Collection<Target> targets = state.getTargetsSelectedWithDialog();

    return new TargetsForReadingSupplier(devices, runningDeviceTargets, targets).getDialogTargets();
  }

  void setTargetsSelectedWithDialog(@NotNull Set<@NotNull Target> targetsSelectedWithDialog) {
    State state = myPersistentStateComponent.getState();
    TargetsForWritingSupplier supplier = new TargetsForWritingSupplier(state.getTargetsSelectedWithDialog(), targetsSelectedWithDialog);

    state.setRunningDeviceTargetsSelectedWithDialog(supplier.getDialogRunningDeviceTargets());
    state.setTargetsSelectedWithDialog(supplier.getDialogTargets());
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
    @OptionTag(tag = "runningDeviceTargetSelectedWithDropDown", nameAttribute = "")
    public @Nullable TargetState runningDeviceTargetSelectedWithDropDown;

    @OptionTag(tag = "targetSelectedWithDropDown", nameAttribute = "")
    public @Nullable TargetState targetSelectedWithDropDown;

    @OptionTag(tag = "timeTargetWasSelectedWithDropDown", nameAttribute = "", converter = InstantConverter.class)
    public @Nullable Instant timeTargetWasSelectedWithDropDown;

    @OptionTag(tag = "multipleDevicesSelectedInDropDown", nameAttribute = "")
    public boolean multipleDevicesSelectedInDropDown;

    @XCollection(style = Style.v2)
    public @NotNull Collection<@NotNull TargetState> runningDeviceTargetsSelectedWithDialog = Collections.emptyList();

    @XCollection(style = Style.v2)
    public @NotNull Collection<@NotNull TargetState> targetsSelectedWithDialog = Collections.emptyList();

    private @Nullable RunningDeviceTarget getRunningDeviceTargetSelectedWithDropDown() {
      return (RunningDeviceTarget)getTargetSelectedWithDropDown(runningDeviceTargetSelectedWithDropDown);
    }

    private void setRunningDeviceTargetSelectedWithDropDown(@Nullable RunningDeviceTarget runningDeviceTargetSelectedWithDropDown) {
      if (runningDeviceTargetSelectedWithDropDown == null) {
        this.runningDeviceTargetSelectedWithDropDown = null;
        return;
      }

      this.runningDeviceTargetSelectedWithDropDown = new TargetState(runningDeviceTargetSelectedWithDropDown);
    }

    private @Nullable Target getTargetSelectedWithDropDown() {
      return getTargetSelectedWithDropDown(targetSelectedWithDropDown);
    }

    private void setTargetSelectedWithDropDown(@Nullable Target targetSelectedWithDropDown) {
      if (targetSelectedWithDropDown == null) {
        this.targetSelectedWithDropDown = null;
        return;
      }

      this.targetSelectedWithDropDown = new TargetState(targetSelectedWithDropDown);
    }

    private static @Nullable Target getTargetSelectedWithDropDown(@Nullable TargetState targetState) {
      if (targetState == null) {
        return null;
      }

      try {
        return targetState.asTarget();
      }
      catch (DevicesSelectedServiceException exception) {
        Logger.getInstance(DevicesSelectedService.class).warn(exception);
        return null;
      }
    }

    private @NotNull Collection<@NotNull RunningDeviceTarget> getRunningDeviceTargetsSelectedWithDialog() {
      return getTargetsSelectedWithDialog(runningDeviceTargetsSelectedWithDialog, RunningDeviceTarget.class);
    }

    private void setRunningDeviceTargetsSelectedWithDialog(@NotNull Collection<@NotNull RunningDeviceTarget> runningDeviceTargetsSelectedWithDialog) {
      this.runningDeviceTargetsSelectedWithDialog = asTargetStates(runningDeviceTargetsSelectedWithDialog);
    }

    private @NotNull Collection<@NotNull Target> getTargetsSelectedWithDialog() {
      return getTargetsSelectedWithDialog(targetsSelectedWithDialog, Target.class);
    }

    private void setTargetsSelectedWithDialog(@NotNull Collection<@NotNull Target> targetsSelectedWithDialog) {
      this.targetsSelectedWithDialog = asTargetStates(targetsSelectedWithDialog);
    }

    private static <T> @NotNull Collection<@NotNull T> getTargetsSelectedWithDialog(@NotNull Collection<@NotNull TargetState> targetStates,
                                                                                    @NotNull Class<@NotNull T> c) {
      try {
        Collection<T> targets = new ArrayList<>(targetStates.size());

        for (TargetState targetState : targetStates) {
          targets.add(c.cast(targetState.asTarget()));
        }

        return targets;
      }
      catch (DevicesSelectedServiceException exception) {
        Logger.getInstance(DevicesSelectedService.class).warn(exception);
        return Collections.emptyList();
      }
    }

    private static <T extends Target> @NotNull Collection<@NotNull TargetState> asTargetStates(@NotNull Collection<@NotNull T> targets) {
      return targets.stream()
        .map(TargetState::new)
        .collect(Collectors.toList());
    }

    @Override
    public int hashCode() {
      return Objects.hash(runningDeviceTargetSelectedWithDropDown,
                          targetSelectedWithDropDown,
                          timeTargetWasSelectedWithDropDown,
                          multipleDevicesSelectedInDropDown,
                          runningDeviceTargetsSelectedWithDialog,
                          targetsSelectedWithDialog);
    }

    @Override
    public boolean equals(@Nullable Object object) {
      if (!(object instanceof State)) {
        return false;
      }

      State state = (State)object;

      return Objects.equals(runningDeviceTargetSelectedWithDropDown, state.runningDeviceTargetSelectedWithDropDown) &&
             Objects.equals(targetSelectedWithDropDown, state.targetSelectedWithDropDown) &&
             Objects.equals(timeTargetWasSelectedWithDropDown, state.timeTargetWasSelectedWithDropDown) &&
             multipleDevicesSelectedInDropDown == state.multipleDevicesSelectedInDropDown &&
             runningDeviceTargetsSelectedWithDialog.equals(state.runningDeviceTargetsSelectedWithDialog) &&
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
      return Objects.hash(type, deviceKey, snapshotKey);
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
      return Objects.hash(type, value);
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
}
