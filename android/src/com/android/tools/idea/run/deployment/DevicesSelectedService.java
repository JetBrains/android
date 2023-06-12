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

import com.android.tools.idea.run.util.InstantConverter;
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
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The interface between the deployment target drop down and ${PROJECT}/.idea/deploymentTargetDropDown.xml. deploymentTargetDropDown.xml has
 * two broad sets of fields:
 *
 * <ol>
 * <li>A set for the single selection in the drop down: runningDeviceTargetSelectedWithDropDown, targetSelectedWithDropDown, and
 * timeTargetWasSelectedWithDropDown
 * <li>A set for the multiple selections in the Select Multiple Devices dialog: runningDeviceTargetsSelectedWithDialog and
 * targetsSelectedWithDialog
 * </ol>
 *
 * <p>The drop down and dialog selections are independent of each other. Pixel 4 API 30 can be selected in the drop down while Pixel 3 API
 * 30 is selected in the dialog. multipleDevicesSelectedInDropDown tracks the active selection. If it's true, the dialog selection is the
 * active one and the drop down button's text will render something like "Multiple Devices (2)". If it's false, the drop down selection is
 * the active one.
 *
 * <p>runningDeviceTargetSelectedWithDropDown and runningDeviceTargetsSelectedWithDialog will always refer to RunningDeviceTargets.
 * targetSelectedWithDropDown and targetsSelectedWithDialog will always refer to ColdBoot, QuickBoot, or BootWithSnapshotTargets and never
 * to RunningDeviceTargets.
 *
 * <p>Eventually we want running devices to tell Android Studio how they were booted. A user can select a BootWithSnapshotTarget with Studio
 * and then cold boot the device with the terminal. I assume that any running device was booted in an unknown way because of this.
 *
 * <p>When the drop down asks DevicesSelectedService for the current selections, TargetsForReadingSupplier takes the selections and the list
 * of devices and replaces ColdBoot, QuickBoot, and BootWithSnapshotTargets with RunningDeviceTargets if the devices are running. When the
 * drop down asks DevicesSelectedService to save new selections, TargetsForWritingSupplier takes the selections and splits out the
 * RunningDeviceTargets so they're saved separately. Keeping the RunningDeviceTarget selections separate means any ColdBoot, QuickBoot, and
 * BootWithSnapshotTarget selections are restored when the devices are stopped.
 *
 * <p>Targets saved in deploymentTargetDropDown.xml correspond to actual user selections with the drop down or dialog. Note that there is a
 * lot of default selection logic in getTargetSelectedWithComboBox. The result of that logic is <em>not</em> saved in
 * deploymentTargetDropDown.xml because those aren't user selections.
 *
 * <p>timeTargetWasSelectedWithDropDown is the timestamp of the user's last drop down selection. If it falls before the connection time of a
 * newly connected device, that newly connected device is returned by the default selection logic in getTargetSelectedWithComboBox.
 * Otherwise the user's selection takes precedence.
 */
final class DevicesSelectedService {
  private final @NotNull PersistentStateComponent myPersistentStateComponent;

  @NotNull
  private final Clock myClock;

  @SuppressWarnings("unused")
  private DevicesSelectedService(@NotNull Project project) {
    this(project.getService(PersistentStateComponent.class), Clock.systemDefaultZone());
  }

  @VisibleForTesting
  @NonInjectable
  DevicesSelectedService(@NotNull PersistentStateComponent persistentStateComponent, @NotNull Clock clock) {
    myPersistentStateComponent = persistentStateComponent;
    myClock = clock;
  }

  @NotNull
  static DevicesSelectedService getInstance(@NotNull Project project) {
    return project.getService(DevicesSelectedService.class);
  }

  @NotNull Optional<Target> getTargetSelectedWithComboBox(@NotNull List<Device> devices) {
    if (devices.isEmpty()) {
      return Optional.empty();
    }

    State state = myPersistentStateComponent.getState();

    TargetsForReadingSupplier supplier = new TargetsForReadingSupplier(devices,
                                                                       state.getRunningDeviceTargetSelectedWithDropDown(),
                                                                       state.getTargetSelectedWithDropDown());

    // The user selected a running target, but it's no longer running. Clear it from the persisted state.
    //
    // If targetSelectedWithDropDown is null, then timeTargetWasSelectedWithDropDown is the time that running target was selected, so clear
    // it as well
    //
    // If targetSelectedWithDropDown isn't null, then they selected an available target, launched it, and selected the same target again. Do
    // not clear the time because the following code expects selections to always have times associated with them and the first selection is
    // still referred to by targetSelectedWithDropDown.
    if (supplier.getDropDownRunningDeviceTargetToRemove().isPresent()) {
      state.runningDeviceTargetSelectedWithDropDown = null;

      if (state.targetSelectedWithDropDown == null) {
        state.timeTargetWasSelectedWithDropDown = null;
      }
    }

    Target target = supplier.getDropDownTarget().orElse(null);

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
    return myPersistentStateComponent.getState().multipleDevicesSelectedInDropDown;
  }

  void setMultipleDevicesSelectedInComboBox(boolean multipleDevicesSelectedInComboBox) {
    myPersistentStateComponent.getState().multipleDevicesSelectedInDropDown = multipleDevicesSelectedInComboBox;
  }

  @NotNull Set<Target> getTargetsSelectedWithDialog(@NotNull List<Device> devices) {
    State state = myPersistentStateComponent.getState();

    Collection<RunningDeviceTarget> runningDeviceTargets = state.getRunningDeviceTargetsSelectedWithDialog();
    TargetsForReadingSupplier supplier = new TargetsForReadingSupplier(devices, runningDeviceTargets, state.getTargetsSelectedWithDialog());

    runningDeviceTargets.removeAll(supplier.getDialogRunningDeviceTargetsToRemove());
    state.setRunningDeviceTargetsSelectedWithDialog(runningDeviceTargets);

    return supplier.getDialogTargets();
  }

  void setTargetsSelectedWithDialog(@NotNull Set<Target> targetsSelectedWithDialog) {
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
    public @NotNull Collection<TargetState> runningDeviceTargetsSelectedWithDialog = Collections.emptyList();

    @XCollection(style = Style.v2)
    public @NotNull Collection<TargetState> targetsSelectedWithDialog = Collections.emptyList();

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

    private @NotNull Collection<RunningDeviceTarget> getRunningDeviceTargetsSelectedWithDialog() {
      return getTargetsSelectedWithDialog(runningDeviceTargetsSelectedWithDialog, RunningDeviceTarget.class);
    }

    private void setRunningDeviceTargetsSelectedWithDialog(@NotNull Collection<RunningDeviceTarget> runningDeviceTargetsSelectedWithDialog) {
      this.runningDeviceTargetsSelectedWithDialog = asTargetStates(runningDeviceTargetsSelectedWithDialog);
    }

    private @NotNull Collection<Target> getTargetsSelectedWithDialog() {
      return getTargetsSelectedWithDialog(targetsSelectedWithDialog, Target.class);
    }

    private void setTargetsSelectedWithDialog(@NotNull Collection<Target> targetsSelectedWithDialog) {
      this.targetsSelectedWithDialog = asTargetStates(targetsSelectedWithDialog);
    }

    private static <T> @NotNull Collection<T> getTargetsSelectedWithDialog(@NotNull Collection<TargetState> targetStates,
                                                                                    @NotNull Class<T> c) {
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

    private static <T extends Target> @NotNull Collection<TargetState> asTargetStates(@NotNull Collection<T> targets) {
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
