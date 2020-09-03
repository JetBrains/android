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
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.Separator;
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
  public void popupActionGroupRunOnMultipleDevicesActionIsEnabled() {
    // Arrange
    Collection<Device> devices = Collections.emptyList();

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction, () -> true);

    // Assert
    Object[] children = {
      myActionManager.getAction(RunOnMultipleDevicesAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID)};

    assertArrayEquals(children, group.getChildren(null));
  }

  @Test
  public void popupActionGroup() {
    // Arrange
    Collection<Device> devices = Collections.emptyList();

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction, () -> false);

    // Assert
    Object[] children = {
      myActionManager.getAction(SelectMultipleDevicesAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
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
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction, () -> false);

    // Assert
    Object[] children = {
      myActionManager.getAction(Heading.AVAILABLE_DEVICES_ID),
      SelectDeviceAction.newSelectDeviceAction(device, myComboBoxAction),
      Separator.getInstance(),
      myActionManager.getAction(SelectMultipleDevicesAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
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
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction, () -> false);

    // Assert
    Object[] children = {
      myActionManager.getAction(Heading.RUNNING_DEVICES_ID),
      SelectDeviceAction.newSelectDeviceAction(device, myComboBoxAction),
      Separator.getInstance(),
      myActionManager.getAction(SelectMultipleDevicesAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
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
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction, () -> false);

    // Assert
    Object[] children = {
      myActionManager.getAction(Heading.RUNNING_DEVICES_ID),
      SelectDeviceAction.newSelectDeviceAction(runningDevice, myComboBoxAction),
      Separator.getInstance(),
      myActionManager.getAction(Heading.AVAILABLE_DEVICES_ID),
      SelectDeviceAction.newSelectDeviceAction(availableDevice, myComboBoxAction),
      Separator.getInstance(),
      myActionManager.getAction(SelectMultipleDevicesAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID)};

    assertArrayEquals(children, group.getChildren(null));
  }
}
