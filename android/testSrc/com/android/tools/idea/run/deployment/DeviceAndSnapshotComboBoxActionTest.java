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
import static org.junit.Assert.assertFalse;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.util.concurrent.Futures;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.ide.util.ProjectPropertiesComponentImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import icons.StudioIcons;
import java.awt.Component;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public final class DeviceAndSnapshotComboBoxActionTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private AsyncDevicesGetter myDevicesGetter;
  private ExecutionTargetService myExecutionTargetService;
  private DevicesSelectedService myDevicesSelectedService;
  private RunManager myRunManager;

  private Presentation myPresentation;
  private AnActionEvent myEvent;

  @Before
  public void mockDevicesGetter() {
    myDevicesGetter = Mockito.mock(AsyncDevicesGetter.class);
  }

  @Before
  public void mockExecutionTargetService() {
    myExecutionTargetService = Mockito.mock(ExecutionTargetService.class);
  }

  @Before
  public void newDevicesSelectedService() {
    PropertiesComponent properties = new ProjectPropertiesComponentImpl();
    Clock clock = Clock.fixed(Instant.parse("2018-11-28T01:15:27.000Z"), ZoneId.of("America/Los_Angeles"));

    myDevicesSelectedService = new DevicesSelectedService(myRule.getProject(), project -> properties, clock, () -> false);
  }

  @Before
  public void mockRunManager() {
    myRunManager = Mockito.mock(RunManager.class);
  }

  @Before
  public void mockEvent() {
    myPresentation = new Presentation();

    myEvent = Mockito.mock(AnActionEvent.class);
    Mockito.when(myEvent.getProject()).thenReturn(myRule.getProject());
    Mockito.when(myEvent.getPresentation()).thenReturn(myPresentation);
    Mockito.when(myEvent.getPlace()).thenReturn(ActionPlaces.MAIN_TOOLBAR);
  }

  @Test
  public void selectMultipleDevices() {
    // Arrange
    Key key = new VirtualDeviceName("Pixel_4_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(Collections.singletonList(device)));

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    Mockito.when(service.isMultipleDevicesSelectedInComboBox()).thenReturn(true);
    Mockito.when(service.getDeviceKeysSelectedWithDialog()).thenReturn(Collections.singleton(key));

    DialogWrapper dialog = Mockito.mock(DialogWrapper.class);
    Mockito.when(dialog.showAndGet()).thenReturn(true);

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(p -> myDevicesGetter)
      .setDevicesSelectedServiceGetInstance(p -> service)
      .setExecutionTargetServiceGetInstance(p -> myExecutionTargetService)
      .setNewSelectMultipleDevicesDialog((p, devices) -> dialog)
      .build();

    Project project = myRule.getProject();

    // Act
    action.selectMultipleDevices(project);

    // Assert
    Mockito.verify(service).setMultipleDevicesSelectedInComboBox(true);

    Mockito.verify(myExecutionTargetService).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(Collections.singleton(key),
                                                                                                          myDevicesGetter));
  }

  @Test
  public void selectMultipleDevicesDialogSelectionEmpty() {
    // Arrange
    Key key = new VirtualDeviceName("Pixel_4_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(Collections.singletonList(device)));

    DialogWrapper dialog = Mockito.mock(DialogWrapper.class);
    Mockito.when(dialog.showAndGet()).thenReturn(true);

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(p -> myDevicesGetter)
      .setDevicesSelectedServiceGetInstance(p -> myDevicesSelectedService)
      .setExecutionTargetServiceGetInstance(p -> myExecutionTargetService)
      .setNewSelectMultipleDevicesDialog((p, devices) -> dialog)
      .build();

    Project project = myRule.getProject();

    // Act
    action.selectMultipleDevices(project);

    // Assert
    assertFalse(myDevicesSelectedService.isMultipleDevicesSelectedInComboBox());

    Mockito.verify(myExecutionTargetService).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(Collections.singleton(key),
                                                                                                          myDevicesGetter));
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
  public void createCustomComponentDoesntScaleGroupLayoutDefaultSize() {
    // Arrange
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .build();

    // Act
    action.createCustomComponent(myPresentation, i -> 4 * i);
  }

  @Test
  public void updateDevicesIsntPresent() {
    // Arrange
    AnAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .build();

    // Act
    action.update(myEvent);

    // Assert
    assertFalse(myPresentation.isEnabled());
    assertEquals("Loading Devices...", myPresentation.getText());
  }

  @Test
  public void updateDoesntClearSelectedDeviceWhenDevicesIsEmpty() {
    // Arrange
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setDevicesSelectedServiceGetInstance(project -> myDevicesSelectedService)
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .setGetRunManager(project -> myRunManager)
      .build();

    Device pixel2XlApiQ = new VirtualDevice.Builder()
      .setName("Pixel 2 XL API Q")
      .setKey(new VirtualDeviceName("Pixel_2_XL_API_Q"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device pixel3XlApiQ = new VirtualDevice.Builder()
      .setName("Pixel 3 XL API Q")
      .setKey(new VirtualDeviceName("Pixel_3_XL_API_Q"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    // Act
    myDevicesSelectedService.setDeviceSelectedWithComboBox(pixel3XlApiQ);
    action.update(myEvent);

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(Arrays.asList(pixel2XlApiQ, pixel3XlApiQ)));
    action.update(myEvent);

    // Assert
    assertEquals(pixel3XlApiQ, action.getSelectedDevice(myRule.getProject()));
  }

  @Test
  public void updateDeviceRunsAfterExecutionTargetIsCreated() {
    // Arrange
    Device availableDevice = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(new VirtualDeviceName("Pixel_4_API_30"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(Collections.singletonList(availableDevice)));

    ExecutionTargetManager manager = new FakeExecutionTargetManager();

    RunConfiguration configuration = Mockito.mock(RunConfiguration.class);

    RunnerAndConfigurationSettings configurationAndSettings = Mockito.mock(RunnerAndConfigurationSettings.class);
    Mockito.when(configurationAndSettings.getConfiguration()).thenReturn(configuration);

    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(configurationAndSettings);
    Mockito.when(myRunManager.findSettings(configuration)).thenReturn(configurationAndSettings);

    ExecutionTargetService service = new ExecutionTargetService(myRule.getProject(), project -> manager, project -> myRunManager);

    AnAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setDevicesSelectedServiceGetInstance(project -> myDevicesSelectedService)
      .setExecutionTargetServiceGetInstance(project -> service)
      .setGetRunManager(project -> myRunManager)
      .build();

    // Act
    action.update(myEvent);

    IDevice device = Mockito.mock(IDevice.class);

    AndroidDevice androidDevice = Mockito.mock(AndroidDevice.class);
    Mockito.when(androidDevice.isRunning()).thenReturn(true);
    Mockito.when(androidDevice.getLaunchedDevice()).thenReturn(Futures.immediateFuture(device));

    Device runningDevice = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(new VirtualDeviceName("Pixel_4_API_30"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(androidDevice)
      .build();

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(Collections.singletonList(runningDevice)));

    // Assert
    AndroidExecutionTarget target = service.getActiveTarget();

    assertEquals(Collections.singletonList(device), target.getRunningDevices());
    assertEquals("Pixel 4 API 30", target.getDisplayName());
    assertEquals(VirtualDevice.ourConnectedIcon, target.getIcon());
  }
}
