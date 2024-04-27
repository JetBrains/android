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
package com.android.tools.idea.run.deployment.legacyselector;

import static org.junit.Assert.assertArrayEquals;

import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiAction;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.deployment.Heading;
import com.android.tools.idea.run.deployment.legacyselector.Device.Type;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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

  private AnAction myRunningDevicesHeading;
  private AnAction myAvailableDevicesHeading;
  private AnAction mySelectMultipleDevicesAction;
  private AnAction myPairDevicesUsingWiFiAction;
  private AnAction myDeviceManagerAction;
  private AnAction myOpenConnectionAssistantSidePanelAction;

  private ActionManager myActionManager;

  @Before
  public void mockComboBoxAction() {
    myComboBoxAction = Mockito.mock(DeviceAndSnapshotComboBoxAction.class);
  }

  @Before
  public void mockActionManager() {
    myRunningDevicesHeading = Mockito.mock(AnAction.class);
    myAvailableDevicesHeading = Mockito.mock(AnAction.class);
    mySelectMultipleDevicesAction = Mockito.mock(AnAction.class);
    myPairDevicesUsingWiFiAction = Mockito.mock(AnAction.class);
    myDeviceManagerAction = Mockito.mock(AnAction.class);
    myOpenConnectionAssistantSidePanelAction = Mockito.mock(AnAction.class);

    myActionManager = Mockito.mock(ActionManager.class);

    Mockito.when(myActionManager.getAction(Heading.RUNNING_DEVICES_ID)).thenReturn(myRunningDevicesHeading);
    Mockito.when(myActionManager.getAction(Heading.AVAILABLE_DEVICES_ID)).thenReturn(myAvailableDevicesHeading);
    Mockito.when(myActionManager.getAction(SelectMultipleDevicesAction.ID)).thenReturn(mySelectMultipleDevicesAction);
    Mockito.when(myActionManager.getAction(PairDevicesUsingWiFiAction.ID)).thenReturn(myPairDevicesUsingWiFiAction);
    Mockito.when(myActionManager.getAction("Android.DeviceManager")).thenReturn(myDeviceManagerAction);
    Mockito.when(myActionManager.getAction("DeveloperServices.ConnectionAssistant")).thenReturn(myOpenConnectionAssistantSidePanelAction);
  }

  @Test
  public void popupActionGroup() {
    // Arrange
    Collection<Device> devices = Collections.emptyList();

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction, myActionManager);

    // Assert
    Object[] children = {
      mySelectMultipleDevicesAction,
      myPairDevicesUsingWiFiAction,
      myDeviceManagerAction,
      Separator.getInstance(),
      myOpenConnectionAssistantSidePanelAction};

    assertArrayEquals(children, group.getChildren(null));
  }

  @Test
  public void newSelectDeviceActionsOrSnapshotActionGroupsRunningDevicesPresent() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_4_API_30)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Device> devices = Collections.singletonList(device);

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction, myActionManager);

    // Assert
    Object[] children = {
      myRunningDevicesHeading,
      new SelectDeviceAction(device, myComboBoxAction),
      Separator.getInstance(),
      mySelectMultipleDevicesAction,
      myPairDevicesUsingWiFiAction,
      myDeviceManagerAction,
      Separator.getInstance(),
      myOpenConnectionAssistantSidePanelAction};

    assertArrayEquals(children, group.getChildren(null));
  }

  @Test
  public void newSelectDeviceActionsOrSnapshotActionGroupsRunningDevicesPresentAndAvailableDevicesPresent() {
    // Arrange
    Device runningDevice = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_4_API_30)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device availableDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API 30")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_4_API_30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Device> devices = Arrays.asList(runningDevice, availableDevice);

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction, myActionManager);

    // Assert
    Object[] children = {
      myRunningDevicesHeading,
      new SelectDeviceAction(runningDevice, myComboBoxAction),
      Separator.getInstance(),
      myAvailableDevicesHeading,
      new SelectDeviceAction(availableDevice, myComboBoxAction),
      Separator.getInstance(),
      mySelectMultipleDevicesAction,
      myPairDevicesUsingWiFiAction,
      myDeviceManagerAction,
      Separator.getInstance(),
      myOpenConnectionAssistantSidePanelAction};

    assertArrayEquals(children, group.getChildren(null));
  }

  @Test
  public void newSelectDeviceActionOrSnapshotActionGroup() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_4_API_30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .addSnapshot(new Snapshot(Keys.PIXEL_4_API_30_SNAPSHOT_1))
      .build();

    Collection<Device> devices = Collections.singletonList(device);

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction, myActionManager);

    // Assert
    Object[] children = {
      myAvailableDevicesHeading,
      new SnapshotActionGroup(device, myComboBoxAction),
      Separator.getInstance(),
      mySelectMultipleDevicesAction,
      myPairDevicesUsingWiFiAction,
      myDeviceManagerAction,
      Separator.getInstance(),
      myOpenConnectionAssistantSidePanelAction};

    assertArrayEquals(children, group.getChildren(null));
  }

  @Test
  public void popupActionGroupAvailableDevicesPresent() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setType(Type.PHONE)
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Device> devices = Collections.singletonList(device);

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction, myActionManager);

    // Assert
    Object[] children = {
      myAvailableDevicesHeading,
      new SelectDeviceAction(device, myComboBoxAction),
      Separator.getInstance(),
      mySelectMultipleDevicesAction,
      myPairDevicesUsingWiFiAction,
      myDeviceManagerAction,
      Separator.getInstance(),
      myOpenConnectionAssistantSidePanelAction};

    assertArrayEquals(children, group.getChildren(null));
  }

  @Test
  public void popupActionGroupRunningDevicesPresent() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setType(Type.PHONE)
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Device> devices = Collections.singletonList(device);

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction, myActionManager);

    // Assert
    Object[] children = {
      myRunningDevicesHeading,
      new SelectDeviceAction(device, myComboBoxAction),
      Separator.getInstance(),
      mySelectMultipleDevicesAction,
      myPairDevicesUsingWiFiAction,
      myDeviceManagerAction,
      Separator.getInstance(),
      myOpenConnectionAssistantSidePanelAction};

    assertArrayEquals(children, group.getChildren(null));
  }

  @Test
  public void popupActionGroupRunningDevicesPresentAndAvailableDevicesPresent() {
    // Arrange
    Device runningDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setType(Type.PHONE)
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device availableDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setType(Type.PHONE)
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Collection<Device> devices = Arrays.asList(runningDevice, availableDevice);

    // Act
    ActionGroup group = new PopupActionGroup(devices, myComboBoxAction, myActionManager);

    // Assert
    Object[] children = {
      myRunningDevicesHeading,
      new SelectDeviceAction(runningDevice, myComboBoxAction),
      Separator.getInstance(),
      myAvailableDevicesHeading,
      new SelectDeviceAction(availableDevice, myComboBoxAction),
      Separator.getInstance(),
      mySelectMultipleDevicesAction,
      myPairDevicesUsingWiFiAction,
      myDeviceManagerAction,
      Separator.getInstance(),
      myOpenConnectionAssistantSidePanelAction};

    assertArrayEquals(children, group.getChildren(null));
  }
}
