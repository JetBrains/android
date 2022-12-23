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

import static com.google.common.truth.Truth.assertThat;
import static icons.StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.ddmlib.IDevice;
import com.android.testutils.ImageDiffUtil;
import com.android.tools.idea.execution.common.AndroidExecutionTarget;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.deployment.DevicesSelectedService.PersistentStateComponent;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.util.concurrent.Futures;
import com.intellij.execution.ExecutionTargetManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.IconManager;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.After;
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
    Clock clock = Clock.fixed(Instant.parse("2018-11-28T01:15:27.000Z"), ZoneId.of("America/Los_Angeles"));
    myDevicesSelectedService = new DevicesSelectedService(new PersistentStateComponent(), clock);
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

  @Before
  public void activateIconLoader() throws Throwable {
    IconManager.activate(null);
    IconLoader.activate();
  }

  @After
  public void deactivateIconLoader()  {
    IconManager.deactivate();
    IconLoader.deactivate();
  }

  @Test
  public void setTargetSelectedWithComboBox() {
    // Arrange
    Key key = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(Collections.singletonList(device)));

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(project -> myDevicesGetter)
      .setDevicesSelectedServiceGetInstance(project -> myDevicesSelectedService)
      .setExecutionTargetServiceGetInstance(project -> myExecutionTargetService)
      .build();

    Project project = myRule.getProject();
    Target target = new QuickBootTarget(key);

    // Act
    action.setTargetSelectedWithComboBox(project, target);

    // Assert
    Collection<Target> targets = Collections.singleton(target);

    assertEquals(targets, action.getSelectedTargets(project));
    Mockito.verify(myExecutionTargetService).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(targets, myDevicesGetter));
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

    List<Device> devices = Collections.singletonList(device);

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(devices));

    Set<Target> targets = Collections.singleton(new QuickBootTarget(key));

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    Mockito.when(service.getTargetsSelectedWithDialog(devices)).thenReturn(targets);
    Mockito.when(service.isMultipleDevicesSelectedInComboBox()).thenReturn(true);

    DialogWrapper dialog = Mockito.mock(DialogWrapper.class);
    Mockito.when(dialog.showAndGet()).thenReturn(true);

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(p -> myDevicesGetter)
      .setDevicesSelectedServiceGetInstance(p -> service)
      .setExecutionTargetServiceGetInstance(p -> myExecutionTargetService)
      .setNewSelectMultipleDevicesDialog((p, d) -> dialog)
      .build();

    Project project = myRule.getProject();

    // Act
    action.selectMultipleDevices(project);

    // Assert
    Mockito.verify(service).setMultipleDevicesSelectedInComboBox(true);
    Mockito.verify(myExecutionTargetService).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(targets, myDevicesGetter));
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

    Collection<Target> targets = Collections.singleton(new QuickBootTarget(key));
    Mockito.verify(myExecutionTargetService).setActiveTarget(new DeviceAndSnapshotComboBoxExecutionTarget(targets, myDevicesGetter));
  }

  @Test
  public void createCustomComponent() {
    // Arrange
    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .build();

    myPresentation.setIcon(VIRTUAL_DEVICE_PHONE);

    // noinspection DialogTitleCapitalization
    myPresentation.setText(
      "Pixel 2 API 29 (Failed to parse properties from /usr/local/google/home/juancnuno/.android/avd/Pixel_2_API_29.avd/config.ini)",
      false);

    // Act
    Component component = action.createCustomComponent(myPresentation, i -> i);

    // Assert
/* b/263906088
    assertEquals(253, component.getPreferredSize().width);
b/263906088 */
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
      .setType(Device.Type.PHONE)
      .build();

    Device pixel3XlApiQ = new VirtualDevice.Builder()
      .setName("Pixel 3 XL API Q")
      .setKey(new VirtualDeviceName("Pixel_3_XL_API_Q"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    // Act
    myDevicesSelectedService.setTargetSelectedWithComboBox(new QuickBootTarget(new VirtualDeviceName("Pixel_3_XL_API_Q")));
    action.update(myEvent);

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(Arrays.asList(pixel2XlApiQ, pixel3XlApiQ)));
    action.update(myEvent);

    // Assert
    assertEquals(Collections.singletonList(pixel3XlApiQ), action.getSelectedDevices(myRule.getProject()));
  }

  @Test
  public void updateDeviceRunsAfterExecutionTargetIsCreated() throws IOException {
    // Arrange
    Device availableDevice = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(new VirtualDeviceName("Pixel_4_API_30"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
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
      .setType(Device.Type.PHONE)
      .build();

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(Collections.singletonList(runningDevice)));

    // Assert
    AndroidExecutionTarget target = service.getActiveTarget();

    assertEquals(Collections.singletonList(device), target.getRunningDevices());
    assertEquals("Pixel 4 API 30", target.getDisplayName());

    BufferedImage expectedIcon =
      ImageUtil.toBufferedImage(IconUtil.toImage(ExecutionUtil.getLiveIndicator(VIRTUAL_DEVICE_PHONE), ScaleContext.createIdentity()));
    BufferedImage actualIcon = ImageUtil.toBufferedImage(IconUtil.toImage(target.getIcon(), ScaleContext.createIdentity()));
    ImageDiffUtil.assertImageSimilar("icon", expectedIcon, actualIcon, 0);
  }

  @Test
  public void isMultipleTargetsSelectedInComboBoxWithOneDeviceSelected() {
    // Arrange
    Key key = new VirtualDeviceName("Pixel_4_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Collections.singletonList(device);

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(devices));

    Set<Target> targets = Collections.singleton(new QuickBootTarget(key));

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    Mockito.when(service.getTargetsSelectedWithDialog(devices)).thenReturn(targets);
    Mockito.when(service.isMultipleDevicesSelectedInComboBox()).thenReturn(true);

    DialogWrapper dialog = Mockito.mock(DialogWrapper.class);
    Mockito.when(dialog.showAndGet()).thenReturn(true);

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(p -> myDevicesGetter)
      .setDevicesSelectedServiceGetInstance(p -> service)
      .setExecutionTargetServiceGetInstance(p -> myExecutionTargetService)
      .setNewSelectMultipleDevicesDialog((p, d) -> dialog)
      .build();

    Project project = myRule.getProject();

    // Act
    action.selectMultipleDevices(project);

    // Assert
    assertThat(action.isMultipleTargetsSelectedInComboBox(project)).isTrue();
    assertThat(action.getNumberOfSelectedDevices(project)).isEqualTo(1);
  }

  @Test
  public void isMultipleTargetsSelectedInComboBoxWithMultipleDevicesSelected() {
    // Arrange
    Key key1 = new VirtualDeviceName("Pixel_4_API_29");
    Key key2 = new VirtualDeviceName("Pixel_4_API_28");

    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setKey(key1)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();
    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 4 API 28")
      .setKey(key2)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = new ArrayList<>();
    devices.add(device1);
    devices.add(device2);

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(devices));

    Set<Target> targets = new HashSet();
    targets.add(new QuickBootTarget(key1));
    targets.add(new QuickBootTarget(key2));

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    Mockito.when(service.getTargetsSelectedWithDialog(devices)).thenReturn(targets);
    Mockito.when(service.isMultipleDevicesSelectedInComboBox()).thenReturn(true);

    DialogWrapper dialog = Mockito.mock(DialogWrapper.class);
    Mockito.when(dialog.showAndGet()).thenReturn(true);

    DeviceAndSnapshotComboBoxAction action = new DeviceAndSnapshotComboBoxAction.Builder()
      .setDevicesGetterGetter(p -> myDevicesGetter)
      .setDevicesSelectedServiceGetInstance(p -> service)
      .setExecutionTargetServiceGetInstance(p -> myExecutionTargetService)
      .setNewSelectMultipleDevicesDialog((p, d) -> dialog)
      .build();

    Project project = myRule.getProject();

    // Act
    action.selectMultipleDevices(project);

    // Assert
    assertThat(action.isMultipleTargetsSelectedInComboBox(project)).isTrue();
    assertThat(action.getNumberOfSelectedDevices(project)).isEqualTo(2);
  }
}
