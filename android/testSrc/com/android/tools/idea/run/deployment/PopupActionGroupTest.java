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

import static org.junit.Assert.assertArrayEquals;

import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiAction;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Separator;
import java.nio.file.FileSystem;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PopupActionGroupTest {
  @Rule
  public final TestRule myRule = AndroidProjectRule.inMemory();

  private DeviceAndSnapshotComboBoxAction myComboBoxAction;
  private ActionManager myActionManager;

  @Before
  public void mockComboBoxAction() {
    myComboBoxAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);
  }

  @Before
  public void initActionManager() {
    myActionManager = ActionManager.getInstance();
  }

  @Test
  public void popupActionGroup() {
    // Arrange
    Collection<Device> devices = Collections.emptyList();

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction);

    // Assert
    Object[] children = {
      myActionManager.getAction(SelectMultipleDevicesAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(WearDevicePairingAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID)};

    assertArrayEquals(children, group.getChildren(null));
  }

  @Test
  public void newSelectDeviceActionsOrSnapshotActionGroupsRunningDevicesPresent() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Device> devices = Collections.singletonList(device);
    Mockito.when(myComboBoxAction.areSnapshotsEnabled()).thenReturn(true);

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction);

    // Assert
    Object[] children = {
      myActionManager.getAction(Heading.RUNNING_DEVICES_ID),
      new SelectDeviceAction(device, myComboBoxAction),
      Separator.getInstance(),
      myActionManager.getAction(SelectMultipleDevicesAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(WearDevicePairingAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID)};

    assertArrayEquals(children, group.getChildren(null));
  }

  @Test
  public void newSelectDeviceActionsOrSnapshotActionGroupsRunningDevicesPresentAndAvailableDevicesPresent() {
    // Arrange
    Device runningDevice = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device availableDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API 30")
      .setKey(new VirtualDevicePath("/home/user/.android/avd/Pixel_3_API_30.avd"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Device> devices = Arrays.asList(runningDevice, availableDevice);
    Mockito.when(myComboBoxAction.areSnapshotsEnabled()).thenReturn(true);

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction);

    // Assert
    Object[] children = {
      myActionManager.getAction(Heading.RUNNING_DEVICES_ID),
      new SelectDeviceAction(runningDevice, myComboBoxAction),
      Separator.getInstance(),
      myActionManager.getAction(Heading.AVAILABLE_DEVICES_ID),
      new SelectDeviceAction(availableDevice, myComboBoxAction),
      Separator.getInstance(),
      myActionManager.getAction(SelectMultipleDevicesAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(WearDevicePairingAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID)};

    assertArrayEquals(children, group.getChildren(null));
  }

  @Test
  public void newSelectDeviceActionOrSnapshotActionGroup() {
    // Arrange
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .addSnapshot(new Snapshot(fileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-07_16-36-58")))
      .build();

    Collection<Device> devices = Collections.singletonList(device);
    Mockito.when(myComboBoxAction.areSnapshotsEnabled()).thenReturn(true);

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction);

    // Assert
    Object[] children = {
      myActionManager.getAction(Heading.AVAILABLE_DEVICES_ID),
      new SnapshotActionGroup(device, myComboBoxAction),
      Separator.getInstance(),
      myActionManager.getAction(SelectMultipleDevicesAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(WearDevicePairingAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID)};

    assertArrayEquals(children, group.getChildren(null));
  }

  @Test
  public void popupActionGroupAvailableDevicesPresent() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Device> devices = Collections.singletonList(device);

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction);

    // Assert
    Object[] children = {
      myActionManager.getAction(Heading.AVAILABLE_DEVICES_ID),
      new SelectDeviceAction(device, myComboBoxAction),
      Separator.getInstance(),
      myActionManager.getAction(SelectMultipleDevicesAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(WearDevicePairingAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID)};

    assertArrayEquals(children, group.getChildren(null));
  }

  @Test
  public void popupActionGroupRunningDevicesPresent() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Device> devices = Collections.singletonList(device);

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction);

    // Assert
    Object[] children = {
      myActionManager.getAction(Heading.RUNNING_DEVICES_ID),
      new SelectDeviceAction(device, myComboBoxAction),
      Separator.getInstance(),
      myActionManager.getAction(SelectMultipleDevicesAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(WearDevicePairingAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID)};

    assertArrayEquals(children, group.getChildren(null));
  }

  @Test
  public void popupActionGroupRunningDevicesPresentAndAvailableDevicesPresent() {
    // Arrange
    Device runningDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device availableDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Device> devices = Arrays.asList(runningDevice, availableDevice);

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction);

    // Assert
    Object[] children = {
      myActionManager.getAction(Heading.RUNNING_DEVICES_ID),
      new SelectDeviceAction(runningDevice, myComboBoxAction),
      Separator.getInstance(),
      myActionManager.getAction(Heading.AVAILABLE_DEVICES_ID),
      new SelectDeviceAction(availableDevice, myComboBoxAction),
      Separator.getInstance(),
      myActionManager.getAction(SelectMultipleDevicesAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(WearDevicePairingAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID)};

    assertArrayEquals(children, group.getChildren(null));
  }
}
