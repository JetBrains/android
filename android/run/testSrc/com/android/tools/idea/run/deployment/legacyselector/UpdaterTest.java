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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.execution.common.DeployableToDevice;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.AndroidRunConfigurationType;
import com.android.tools.idea.run.deployment.legacyselector.Device.Type;
import com.android.tools.idea.run.deployment.legacyselector.DevicesSelectedService.PersistentStateComponent;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.Presentation;
import icons.StudioIcons;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class UpdaterTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private final Presentation myPresentation = new Presentation();

  @NotNull
  private RunManager myRunManager;

  @Before
  public void initRunManager() {
    myRunManager = RunManager.getInstance(myRule.getProject());

    var configuration = myRunManager.createConfiguration("default configuration", AndroidRunConfigurationType.class);
    myRunManager.addConfiguration(configuration);
    myRunManager.setSelectedConfiguration(configuration);
  }

  @Test
  public void updateDependingOnConfigurationConfigurationAndSettingsIsNull() {
    // Arrange
    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(Mockito.mock(DevicesSelectedService.class))
      .build();

    // Act
    updater.update();

    // Assert
    assertFalse(myPresentation.isEnabled());
    assertEquals("Add a run/debug configuration", myPresentation.getDescription());
  }

  @Test
  public void updateDependingOnConfigurationConfigurationDeploysToLocalDevice() {
    // Arrange
    var configuration = Mockito.mock(AndroidRunConfiguration.class);
    Mockito.when(configuration.getUserData(DeployableToDevice.getKEY())).thenReturn(true);

    var updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(Mockito.mock(DevicesSelectedService.class))
      .setConfigurationAndSettings(mockConfigurationAndSettings(configuration))
      .build();

    // Act
    updater.update();

    // Assert
    assertTrue(myPresentation.isEnabled());
    assertNull(myPresentation.getDescription());
  }

  @Test
  public void updateDependingOnConfiguration() {
    // Arrange
    RunConfiguration configuration = Mockito.mock(RunConfiguration.class);
    Mockito.when(configuration.getName()).thenReturn("ExampleUnitTest");

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(Mockito.mock(DevicesSelectedService.class))
      .setConfigurationAndSettings(mockConfigurationAndSettings(configuration))
      .build();

    // Act
    updater.update();

    // Assert
    assertFalse(myPresentation.isEnabled());
    assertEquals("Not applicable for the \"ExampleUnitTest\" configuration", myPresentation.getDescription());
  }

  @NotNull
  private static RunnerAndConfigurationSettings mockConfigurationAndSettings(@NotNull RunConfiguration configuration) {
    RunnerAndConfigurationSettings configurationAndSettings = Mockito.mock(RunnerAndConfigurationSettings.class);
    Mockito.when(configurationAndSettings.getConfiguration()).thenReturn(configuration);

    return configurationAndSettings;
  }

  @Test
  public void updateInToolbarForMultipleDevicesSelectedKeysIsEmptyDevicesIsEmpty() {
    // Arrange
    Key key = new VirtualDeviceName("Pixel_2_API_29");

    DevicesSelectedService devicesSelectedService = newDevicesSelectedService();
    devicesSelectedService.setMultipleDevicesSelectedInComboBox(true);
    devicesSelectedService.setTargetsSelectedWithDialog(Collections.singleton(new QuickBootTarget(key)));

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(devicesSelectedService)
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals(Collections.emptySet(), devicesSelectedService.getTargetsSelectedWithDialog(Collections.emptyList()));
    assertFalse(devicesSelectedService.isMultipleDevicesSelectedInComboBox());
    assertEquals(Optional.empty(), devicesSelectedService.getTargetSelectedWithComboBox(Collections.emptyList()));

    assertNull(myPresentation.getIcon());
    assertEquals("No Devices", myPresentation.getText());
  }

  @Test
  public void updateInToolbarForMultipleDevicesSelectedKeysIsEmpty() {
    // Arrange
    Key key1 = new VirtualDeviceName("Pixel_2_API_29");

    DevicesSelectedService devicesSelectedService = newDevicesSelectedService();
    devicesSelectedService.setMultipleDevicesSelectedInComboBox(true);
    devicesSelectedService.setTargetsSelectedWithDialog(Collections.singleton(new QuickBootTarget(key1)));

    Key key2 = new VirtualDeviceName("Pixel_3_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setType(Type.PHONE)
      .setKey(key2)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Collections.singletonList(device);

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(devicesSelectedService)
      .setDevices(devices)
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals(Collections.emptySet(), devicesSelectedService.getTargetsSelectedWithDialog(devices));
    assertFalse(devicesSelectedService.isMultipleDevicesSelectedInComboBox());
    assertEquals(Optional.of(new QuickBootTarget(key2)), devicesSelectedService.getTargetSelectedWithComboBox(devices));

    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE, myPresentation.getIcon());
    assertEquals("Pixel 3 API 29", myPresentation.getText());
  }

  @Test
  public void updateInToolbarForMultipleDevices() {
    // Arrange
    Key key = new VirtualDeviceName("Pixel_2_API_29");
    Target target = new QuickBootTarget(key);
    Set<Target> targets = Collections.singleton(target);

    DevicesSelectedService devicesSelectedService = newDevicesSelectedService();
    devicesSelectedService.setMultipleDevicesSelectedInComboBox(true);
    devicesSelectedService.setTargetsSelectedWithDialog(targets);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 2 API 29")
      .setType(Type.PHONE)
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Collections.singletonList(device);

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(devicesSelectedService)
      .setDevices(devices)
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals(targets, devicesSelectedService.getTargetsSelectedWithDialog(devices));
    assertTrue(devicesSelectedService.isMultipleDevicesSelectedInComboBox());
    assertEquals(Optional.of(target), devicesSelectedService.getTargetSelectedWithComboBox(devices));

    assertEquals(StudioIcons.DeviceExplorer.MULTIPLE_DEVICES, myPresentation.getIcon());
    assertEquals("Multiple Devices (1)", myPresentation.getText());
  }

  @Test
  public void updateInToolbarForMultipleDevicesDeviceIsLaunchedAfterTargetWasSelected() {
    // Arrange
    DevicesSelectedService service = newDevicesSelectedService();
    service.setMultipleDevicesSelectedInComboBox(true);
    service.setTargetsSelectedWithDialog(Collections.singleton(new BootWithSnapshotTarget(Keys.PIXEL_4_API_30, Keys.PIXEL_4_API_30_SNAPSHOT_1)));

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_4_API_30)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .addSnapshot(new Snapshot(Keys.PIXEL_4_API_30_SNAPSHOT_1))
      .build();

    List<Device> devices = Collections.singletonList(device);

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(service)
      .setDevices(devices)
      .build();

    // Act
    updater.update();

    // Assert
    Object target = new RunningDeviceTarget(Keys.PIXEL_4_API_30);

    assertEquals(Collections.singleton(target), service.getTargetsSelectedWithDialog(devices));
    assertTrue(service.isMultipleDevicesSelectedInComboBox());
    assertEquals(Optional.of(target), service.getTargetSelectedWithComboBox(devices));

    assertEquals(StudioIcons.DeviceExplorer.MULTIPLE_DEVICES, myPresentation.getIcon());
    assertEquals("Multiple Devices (1)", myPresentation.getText());
  }

  @Test
  public void updateInToolbarForMultipleDevicesRunningDeviceTargetIsReplacedWithQuickBootTarget() {
    // Arrange
    DevicesSelectedService service = newDevicesSelectedService();
    service.setMultipleDevicesSelectedInComboBox(true);
    service.setTargetsSelectedWithDialog(Collections.singleton(new RunningDeviceTarget(Keys.PIXEL_4_API_30)));

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_4_API_30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .addSnapshot(new Snapshot(Keys.PIXEL_4_API_30_SNAPSHOT_1))
      .build();

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(service)
      .setDevices(Collections.singletonList(device))
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE, myPresentation.getIcon());
    assertEquals("Pixel 4 API 30 - Quick Boot", myPresentation.getText());
  }

  @Test
  public void updateInToolbarForSingleDeviceDevicesIsEmpty() {
    // Arrange
    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(Mockito.mock(DevicesSelectedService.class))
      .build();

    // Act
    updater.update();

    // Assert
    assertNull(myPresentation.getIcon());
    assertEquals("No Devices", myPresentation.getText());
  }

  @Test
  public void updateInToolbarForSingleDevice() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setType(Type.PHONE)
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);

    Mockito.when(service.getTargetSelectedWithComboBox(Collections.singletonList(device)))
      .thenReturn(Optional.of(new QuickBootTarget(new VirtualDeviceName("Pixel_3_API_29"))));

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(service)
      .setDevices(Collections.singletonList(device))
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE, myPresentation.getIcon());
    assertEquals("Pixel 3 API 29", myPresentation.getText());
  }

  @Test
  public void updateInToolbarForSingleDeviceDoesntParseMnemonics() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("apiQ_64_Google")
      .setType(Type.PHONE)
      .setKey(new VirtualDeviceName("apiQ_64_Google"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);

    Mockito.when(service.getTargetSelectedWithComboBox(Collections.singletonList(device)))
      .thenReturn(Optional.of(new QuickBootTarget(new VirtualDeviceName("apiQ_64_Google"))));

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(service)
      .setDevices(Collections.singletonList(device))
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals("apiQ_64_Google", myPresentation.getText());
  }

  @Test
  public void updateInToolbarForSingleDeviceRunningDeviceTargetIsReplacedWithQuickBootTarget() {
    // Arrange
    DevicesSelectedService service = newDevicesSelectedService();
    service.setTargetSelectedWithComboBox(new RunningDeviceTarget(Keys.PIXEL_4_API_30));

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_4_API_30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .addSnapshot(new Snapshot(Keys.PIXEL_4_API_30_SNAPSHOT_1))
      .build();

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(service)
      .setDevices(Collections.singletonList(device))
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE, myPresentation.getIcon());
    assertEquals("Pixel 4 API 30 - Quick Boot", myPresentation.getText());
  }

  @NotNull
  private DevicesSelectedService newDevicesSelectedService() {
    var clock = Clock.fixed(Instant.parse("2018-11-28T01:15:27Z"), ZoneId.of("America/Los_Angeles"));
    return new DevicesSelectedService(new PersistentStateComponent(myRule.getProject()), myRunManager, clock);
  }

  @Test
  public void getTextDeviceHasSnapshot() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_3_API_29)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .addSnapshot(new Snapshot(Keys.PIXEL_3_API_29_SNAPSHOT_1))
      .build();

    List<Device> devices = Collections.singletonList(device);

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);

    Target target = new BootWithSnapshotTarget(Keys.PIXEL_3_API_29, Keys.PIXEL_3_API_29_SNAPSHOT_1);
    Mockito.when(service.getTargetSelectedWithComboBox(devices)).thenReturn(Optional.of(target));

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(service)
      .setDevices(devices)
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals("Pixel 3 API 29 - snap_2018-08-07_16-27-58", myPresentation.getText());
  }

  @Test
  public void getTextDeviceNamesAreEqual() {
    // Arrange
    Device device1 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("00fff9d2279fa601"))
      .setIcon(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)
      .setName("LGE Nexus 5X")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("00fff9d2279fa602"))
      .setIcon(StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE)
      .setName("LGE Nexus 5X")
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);

    Mockito.when(service.getTargetSelectedWithComboBox(Arrays.asList(device1, device2)))
      .thenReturn(Optional.of(new RunningDeviceTarget(new SerialNumber("00fff9d2279fa601"))));

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(service)
      .setDevices(Arrays.asList(device1, device2))
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals("LGE Nexus 5X [00fff9d2279fa601]", myPresentation.getText());
  }

  @Test
  public void getTextDeviceIsConnected() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setType(Type.PHONE)
      .setKey(Keys.PIXEL_4_API_30)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Collections.singletonList(device);

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    Mockito.when(service.getTargetSelectedWithComboBox(devices)).thenReturn(Optional.of(new QuickBootTarget(Keys.PIXEL_4_API_30)));

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(service)
      .setDevices(devices)
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals("Pixel 4 API 30", myPresentation.getText());
  }

  @Test
  public void getTextSnapshotsIsEmpty() {
    // Arrange
    List<Device> devices = Collections.singletonList(TestDevices.buildPixel4Api30());

    DevicesSelectedService service = Mockito.mock(DevicesSelectedService.class);
    Mockito.when(service.getTargetSelectedWithComboBox(devices)).thenReturn(Optional.of(new QuickBootTarget(Keys.PIXEL_4_API_30)));

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(service)
      .setDevices(devices)
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals("Pixel 4 API 30", myPresentation.getText());
  }

  @Test
  public void updatePlaceEqualsMainMenu() {
    // Arrange
    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setPlace(ActionPlaces.MAIN_MENU)
      .setDevicesSelectedService(Mockito.mock(DevicesSelectedService.class))
      .build();

    // Act
    updater.update();

    // Assert
    assertTrue(myPresentation.isVisible());
    assertNull(myPresentation.getIcon());
    assertEquals("Select Device...", myPresentation.getText());
  }

  @Test
  public void updatePlaceEqualsActionSearch() {
    // Arrange
    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setPlace(ActionPlaces.ACTION_SEARCH)
      .setDevicesSelectedService(Mockito.mock(DevicesSelectedService.class))
      .build();

    // Act
    updater.update();

    // Assert
    assertTrue(myPresentation.isVisible());
    assertNull(myPresentation.getIcon());
    assertEquals("Select Device...", myPresentation.getText());
  }

  @Test
  public void updatePlaceEqualsKeyboardShortcut() {
    // Arrange
    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setPlace(ActionPlaces.KEYBOARD_SHORTCUT)
      .setDevicesSelectedService(Mockito.mock(DevicesSelectedService.class))
      .build();

    // Act
    updater.update();

    // Assert
    assertTrue(myPresentation.isVisible());
    assertNull(myPresentation.getIcon());
    assertEquals("Select Device...", myPresentation.getText());
  }
}
