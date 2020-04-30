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

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.adb.wireless.PairDevicesUsingWiFiAction;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.execution.RunManager;
import com.intellij.ide.util.ProjectPropertiesComponentImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import icons.StudioIcons;
import java.awt.Component;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.JComponent;
import org.jetbrains.android.actions.RunAndroidAvdManagerAction;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class DeviceAndSnapshotComboBoxActionTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private PropertiesComponent myProperties;
  private ExecutionTargetService myExecutionTargetService;
  private AsyncDevicesGetter myDevicesGetter;
  private DevicesSelectedService myDevicesSelectedService;

  private RunManager myRunManager;

  private Project myProject;
  private Presentation myPresentation;
  private AnActionEvent myEvent;

  private DataContext myContext;

  private ActionManager myActionManager;

  @Before
  public void newDevicesSelectedService() {
    myProperties = new ProjectPropertiesComponentImpl();
    myExecutionTargetService = Mockito.mock(ExecutionTargetService.class);
    myDevicesGetter = Mockito.mock(AsyncDevicesGetter.class);

    myDevicesSelectedService = new DevicesSelectedService.Builder()
      .setProject(myRule.getProject())
      .setPropertiesComponentGetInstance(project -> myProperties)
      .setClock(Clock.fixed(Instant.parse("2018-11-28T01:15:27.000Z"), ZoneId.of("America/Los_Angeles")))
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .setAsyncDevicesGetterGetInstance(project -> myDevicesGetter)
      .build();
  }

  @Before
  public void mockRunManager() {
    myRunManager = Mockito.mock(RunManager.class);
  }

  @Before
  public void mockEvent() {
    myProject = myRule.getProject();
    myPresentation = new Presentation();

    myEvent = Mockito.mock(AnActionEvent.class);

    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
    Mockito.when(myEvent.getProject()).thenReturn(myProject);
    Mockito.when(myEvent.getPlace()).thenReturn(ActionPlaces.MAIN_TOOLBAR);
  }

  @Before
  public void mockContext() {
    myContext = Mockito.mock(DataContext.class);
    Mockito.when(myContext.getData(CommonDataKeys.PROJECT)).thenReturn(myRule.getProject());
  }

  @Before
  public void initActionManager() {
    myActionManager = ActionManager.getInstance();
  }

  @Test
  public void createCustomComponent() {
    // Arrange
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .build();

    myPresentation.setIcon(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE);

    // noinspection DialogTitleCapitalization
    myPresentation.setText(
      "Pixel 2 API 29 (Failed to parse properties from /usr/local/google/home/juancnuno/.android/avd/Pixel_2_API_29.avd/config.ini)",
      false);

    // Act
    Component component = action.createCustomComponent(myPresentation, i -> i);

    // Assert
    assertEquals(253, component.getPreferredSize().width);
  }

  @Test
  public void createPopupActionGroupActionsIsEmpty() {
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setSelectDeviceSnapshotComboBoxSnapshotsEnabled(() -> true)
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .setDevicesSelectedServiceGetInstance(project -> myDevicesSelectedService)
      .setGetRunManager(project -> myRunManager)
      .build();

    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      myActionManager.getAction(MultipleDevicesAction.ID),
      myActionManager.getAction(ModifyDeviceSetAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID));

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDeviceIsVirtual() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(TestDevices.PIXEL_2_XL_API_28)
      .setKey(new Key("Pixel_2_XL_API_28"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class));

    Device device = builder.build();
    Mockito.when(myDevicesGetter.get()).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setSelectDeviceSnapshotComboBoxSnapshotsEnabled(() -> true)
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .setDevicesSelectedServiceGetInstance(project -> myDevicesSelectedService)
      .setGetRunManager(project -> myRunManager)
      .build();

    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      myActionManager.getAction(Heading.AVAILABLE_DEVICES_ID),
      SelectDeviceAction.newSelectDeviceAction(builder.build(), action),
      Separator.getInstance(),
      myActionManager.getAction(MultipleDevicesAction.ID),
      myActionManager.getAction(ModifyDeviceSetAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID));

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDeviceIsPhysical() {
    Device.Builder builder = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey(new Key("00fff9d2279fa601"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class));

    Device device = builder.build();
    Mockito.when(myDevicesGetter.get()).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .setDevicesSelectedServiceGetInstance(project -> myDevicesSelectedService)
      .setGetRunManager(project -> myRunManager)
      .build();

    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      myActionManager.getAction(Heading.RUNNING_DEVICES_ID),
      SelectDeviceAction.newSelectDeviceAction(builder.build(), action),
      Separator.getInstance(),
      myActionManager.getAction(MultipleDevicesAction.ID),
      myActionManager.getAction(ModifyDeviceSetAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID));

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDevicesAreVirtualAndPhysical() {
    Device.Builder virtualDeviceBuilder = new VirtualDevice.Builder()
      .setName(TestDevices.PIXEL_2_XL_API_28)
      .setKey(new Key("Pixel_2_XL_API_28"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class));

    Device.Builder physicalDeviceBuilder = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey(new Key("00fff9d2279fa601"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27.000Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class));

    Device device1 = virtualDeviceBuilder.build();
    Device device2 = physicalDeviceBuilder.build();

    Mockito.when(myDevicesGetter.get()).thenReturn(Arrays.asList(device1, device2));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .setDevicesSelectedServiceGetInstance(project -> myDevicesSelectedService)
      .setGetRunManager(project -> myRunManager)
      .build();

    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      myActionManager.getAction(Heading.RUNNING_DEVICES_ID),
      SelectDeviceAction.newSelectDeviceAction(physicalDeviceBuilder.build(), action),
      Separator.getInstance(),
      myActionManager.getAction(Heading.AVAILABLE_DEVICES_ID),
      SelectDeviceAction.newSelectDeviceAction(virtualDeviceBuilder.build(), action),
      Separator.getInstance(),
      myActionManager.getAction(MultipleDevicesAction.ID),
      myActionManager.getAction(ModifyDeviceSetAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID));

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupDeviceSnapshotIsDefault() {
    Device.Builder builder = new VirtualDevice.Builder()
      .setName(TestDevices.PIXEL_2_XL_API_28)
      .setKey(new Key("Pixel_2_XL_API_28"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshot(Snapshot.quickboot(FileSystems.getDefault()));

    Device device = builder.build();
    Mockito.when(myDevicesGetter.get()).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setSelectDeviceSnapshotComboBoxSnapshotsEnabled(() -> true)
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .setDevicesSelectedServiceGetInstance(project -> myDevicesSelectedService)
      .setGetRunManager(project -> myRunManager)
      .build();

    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      myActionManager.getAction(Heading.AVAILABLE_DEVICES_ID),
      SelectDeviceAction.newSelectDeviceAction(builder.build(), action),
      Separator.getInstance(),
      myActionManager.getAction(MultipleDevicesAction.ID),
      myActionManager.getAction(ModifyDeviceSetAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID));

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void createPopupActionGroupSnapshotIsntDefault() {
    FileSystem fileSystem = FileSystems.getDefault();

    Device.Builder builder = new VirtualDevice.Builder()
      .setName(TestDevices.PIXEL_2_XL_API_28)
      .setKey(new Key("Pixel_2_XL_API_28"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshot(new Snapshot(fileSystem.getPath("snap_2018-08-07_16-27-58"), fileSystem));

    Device device = builder.build();
    Mockito.when(myDevicesGetter.get()).thenReturn(Collections.singletonList(device));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .setDevicesSelectedServiceGetInstance(project -> myDevicesSelectedService)
      .setGetRunManager(project -> myRunManager)
      .build();

    action.update(myEvent);
    Object actualChildren = Arrays.asList(action.createPopupActionGroup(Mockito.mock(JComponent.class), myContext).getChildren(null));

    Object expectedChildren = Arrays.asList(
      myActionManager.getAction(Heading.AVAILABLE_DEVICES_ID),
      SelectDeviceAction.newSelectDeviceAction(builder.build(), action),
      Separator.getInstance(),
      myActionManager.getAction(MultipleDevicesAction.ID),
      myActionManager.getAction(ModifyDeviceSetAction.ID),
      myActionManager.getAction(PairDevicesUsingWiFiAction.ID),
      myActionManager.getAction(RunAndroidAvdManagerAction.ID));

    assertEquals(expectedChildren, actualChildren);
  }

  @Test
  public void updateDoesntClearSelectedDeviceWhenDevicesIsEmpty() {
    // Arrange
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .setDevicesSelectedServiceGetInstance(project -> myDevicesSelectedService)
      .setGetRunManager(project -> myRunManager)
      .build();

    Device pixel2XlApiQ = new VirtualDevice.Builder()
      .setName("Pixel 2 XL API Q")
      .setKey(new Key("Pixel_2_XL_API_Q"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device pixel3XlApiQ = new VirtualDevice.Builder()
      .setName("Pixel 3 XL API Q")
      .setKey(new Key("Pixel_3_XL_API_Q"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    myDevicesSelectedService.setDeviceSelectedWithComboBox(pixel3XlApiQ);
    action.update(myEvent);

    Mockito.when(myDevicesGetter.get()).thenReturn(Arrays.asList(pixel2XlApiQ, pixel3XlApiQ));
    action.update(myEvent);

    // Assert
    assertEquals(pixel3XlApiQ, action.getSelectedDevice(myProject));
  }

  @Test
  public void updateConnectedDeviceActiveTargetIsSet() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new Key("Pixel_3_API_29"))
      .setConnectionTime(Instant.parse("2020-03-13T23:13:20.913Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myDevicesGetter.get()).thenReturn(Collections.singletonList(device), Collections.singletonList(connectedDevice));

    AnAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .setDevicesSelectedServiceGetInstance(project -> myDevicesSelectedService)
      .setGetRunManager(project -> myRunManager)
      .build();

    // Act
    action.update(myEvent);
    action.update(myEvent);

    // Assert
    Mockito.verify(myExecutionTargetService).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(device));
    Mockito.verify(myExecutionTargetService).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(connectedDevice));
  }
}
