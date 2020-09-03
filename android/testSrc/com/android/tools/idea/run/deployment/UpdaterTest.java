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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.ide.util.ProjectPropertiesComponentImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.Presentation;
import icons.StudioIcons;
import java.nio.file.FileSystem;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
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
  private final DevicesSelectedService myDevicesSelectedService = Mockito.mock(DevicesSelectedService.class);
  private final PropertiesComponent myProperties = new ProjectPropertiesComponentImpl();
  private final AsyncDevicesGetter myDevicesGetter = Mockito.mock(AsyncDevicesGetter.class);

  @Test
  public void updateDependingOnConfigurationConfigurationAndSettingsIsNull() {
    // Arrange
    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(myDevicesSelectedService)
      .build();

    // Act
    updater.update();

    // Assert
    assertFalse(myPresentation.isEnabled());
    assertEquals("Add a run/debug configuration", myPresentation.getDescription());
  }

  @Test
  public void updateDependingOnConfigurationConfigurationInstanceOfAndroidRunConfiguration() {
    // Arrange
    RunConfiguration configuration = Mockito.mock(AndroidRunConfiguration.class);

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(myDevicesSelectedService)
      .setConfigurationAndSettings(mockConfigurationAndSettings(configuration))
      .build();

    // Act
    updater.update();

    // Assert
    assertTrue(myPresentation.isEnabled());
    assertNull(myPresentation.getDescription());
  }

  @Test
  public void updateDependingOnConfigurationConfigurationDeploysToLocalDevice() {
    // Arrange
    RunConfigurationBase<?> configuration = Mockito.mock(RunConfigurationBase.class);
    Mockito.when(configuration.getUserData(DeviceAndSnapshotComboBoxAction.DEPLOYS_TO_LOCAL_DEVICE)).thenReturn(true);

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(myDevicesSelectedService)
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
      .setDevicesSelectedService(myDevicesSelectedService)
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
    DevicesSelectedService devicesSelectedService = buildDevicesSelectedService();
    devicesSelectedService.setMultipleDevicesSelectedInComboBox(true);
    devicesSelectedService.setDeviceKeysSelectedWithDialog(Collections.singleton(new VirtualDeviceName("Pixel_2_API_29")));

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(devicesSelectedService)
      .build();

    // Act
    updater.update();

    // Assert
    assertFalse(myProperties.isValueSet(DevicesSelectedService.DEVICE_KEYS_SELECTED_WITH_DIALOG));
    assertFalse(myProperties.isValueSet(DevicesSelectedService.MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX));
    assertFalse(myProperties.isValueSet(DevicesSelectedService.TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX));
    assertFalse(myProperties.isValueSet(DevicesSelectedService.DEVICE_KEY_SELECTED_WITH_COMBO_BOX));

    assertNull(myPresentation.getIcon());
    assertEquals("No Devices", myPresentation.getText());
  }

  @Test
  public void updateInToolbarForMultipleDevicesSelectedKeysIsEmpty() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(Collections.singletonList(device)));

    DevicesSelectedService devicesSelectedService = buildDevicesSelectedService();
    devicesSelectedService.setMultipleDevicesSelectedInComboBox(true);
    devicesSelectedService.setDeviceKeysSelectedWithDialog(Collections.singleton(new VirtualDeviceName("Pixel_2_API_29")));

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(devicesSelectedService)
      .setDevices(Collections.singletonList(device))
      .build();

    // Act
    updater.update();

    // Assert
    assertFalse(myProperties.isValueSet(DevicesSelectedService.DEVICE_KEYS_SELECTED_WITH_DIALOG));
    assertFalse(myProperties.isValueSet(DevicesSelectedService.MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX));
    assertEquals("VirtualDeviceName@Pixel_3_API_29", myProperties.getValue(DevicesSelectedService.DEVICE_KEY_SELECTED_WITH_COMBO_BOX));
    assertEquals("2018-11-28T01:15:27Z", myProperties.getValue(DevicesSelectedService.TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX));

    assertEquals(StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE, myPresentation.getIcon());
    assertEquals("Pixel 3 API 29", myPresentation.getText());
  }

  @Test
  public void updateInToolbarForMultipleDevices() {
    // Arrange
    Key key = new VirtualDeviceName("Pixel_2_API_29");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 2 API 29")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myDevicesGetter.get()).thenReturn(Optional.of(Collections.singletonList(device)));

    DevicesSelectedService devicesSelectedService = buildDevicesSelectedService();
    devicesSelectedService.setMultipleDevicesSelectedInComboBox(true);
    devicesSelectedService.setDeviceKeysSelectedWithDialog(Collections.singleton(key));

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(devicesSelectedService)
      .setDevices(Collections.singletonList(device))
      .build();

    // Act
    updater.update();

    // Assert
    assertArrayEquals(new String[]{"VirtualDeviceName@Pixel_2_API_29"},
                      myProperties.getValues(DevicesSelectedService.DEVICE_KEYS_SELECTED_WITH_DIALOG));

    assertFalse(myProperties.isValueSet(DevicesSelectedService.TIME_DEVICE_KEY_WAS_SELECTED_WITH_COMBO_BOX));
    assertFalse(myProperties.isValueSet(DevicesSelectedService.DEVICE_KEY_SELECTED_WITH_COMBO_BOX));
    assertTrue(myProperties.getBoolean(DevicesSelectedService.MULTIPLE_DEVICES_SELECTED_IN_COMBO_BOX));

    assertEquals(StudioIcons.DeviceExplorer.MULTIPLE_DEVICES, myPresentation.getIcon());
    assertEquals("Multiple Devices (1)", myPresentation.getText());
  }

  @NotNull
  private DevicesSelectedService buildDevicesSelectedService() {
    return new DevicesSelectedService(myRule.getProject(),
                                      project -> myProperties,
                                      Clock.fixed(Instant.parse("2018-11-28T01:15:27.000Z"), ZoneId.of("America/Los_Angeles")),
                                      () -> false);
  }

  @Test
  public void updateInToolbarForSingleDeviceDevicesIsEmpty() {
    // Arrange
    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(myDevicesSelectedService)
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
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myDevicesSelectedService.getDeviceSelectedWithComboBox(Collections.singletonList(device))).thenReturn(device);

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(myDevicesSelectedService)
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
      .setKey(new VirtualDeviceName("apiQ_64_Google"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myDevicesSelectedService.getDeviceSelectedWithComboBox(Collections.singletonList(device))).thenReturn(device);

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(myDevicesSelectedService)
      .setDevices(Collections.singletonList(device))
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals("apiQ_64_Google", myPresentation.getText());
  }

  @Test
  public void getTextDeviceHasSnapshot() {
    // Arrange
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new NonprefixedKey("Pixel_3_API_29/snap_2018-08-07_16-27-58"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshot(new Snapshot(fileSystem.getPath("snap_2018-08-07_16-27-58"), fileSystem))
      .build();

    Mockito.when(myDevicesSelectedService.getDeviceSelectedWithComboBox(Collections.singletonList(device))).thenReturn(device);

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(myDevicesSelectedService)
      .setDevices(Collections.singletonList(device))
      .setSnapshotsEnabled(true)
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals("Pixel 3 API 29 - snap_2018-08-07_16-27-58", myPresentation.getText());
  }

  @Test
  public void getTextDeviceHasSnapshotAndSnapshotsArentEnabled() {
    // Arrange
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new NonprefixedKey("Pixel_3_API_29/snap_2018-08-07_16-27-58"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshot(new Snapshot(fileSystem.getPath("snap_2018-08-07_16-27-58"), fileSystem))
      .build();

    Mockito.when(myDevicesSelectedService.getDeviceSelectedWithComboBox(Collections.singletonList(device))).thenReturn(device);

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(myDevicesSelectedService)
      .setDevices(Collections.singletonList(device))
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals("Pixel 3 API 29", myPresentation.getText());
  }

  @Test
  public void getTextDeviceNamesAreEqual() {
    // Arrange
    Device device1 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey(new SerialNumber("00fff9d2279fa601"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey(new SerialNumber("00fff9d2279fa602"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Mockito.when(myDevicesSelectedService.getDeviceSelectedWithComboBox(Arrays.asList(device1, device2))).thenReturn(device1);

    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setDevicesSelectedService(myDevicesSelectedService)
      .setDevices(Arrays.asList(device1, device2))
      .build();

    // Act
    updater.update();

    // Assert
    assertEquals("LGE Nexus 5X [00fff9d2279fa601]", myPresentation.getText());
  }

  @Test
  public void updatePlaceEqualsMainMenu() {
    // Arrange
    Updater updater = new Updater.Builder()
      .setProject(myRule.getProject())
      .setPresentation(myPresentation)
      .setPlace(ActionPlaces.MAIN_MENU)
      .setDevicesSelectedService(myDevicesSelectedService)
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
      .setDevicesSelectedService(myDevicesSelectedService)
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
      .setDevicesSelectedService(myDevicesSelectedService)
      .build();

    // Act
    updater.update();

    // Assert
    assertTrue(myPresentation.isVisible());
    assertNull(myPresentation.getIcon());
    assertEquals("Select Device...", myPresentation.getText());
  }
}
