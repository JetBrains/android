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
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.execution.common.DeployableToDevice;
import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.AndroidRunConfiguration;
import com.android.tools.idea.run.deployment.legacyselector.DevicesSelectedService.MapState;
import com.android.tools.idea.run.deployment.legacyselector.DevicesSelectedService.PersistentStateComponent;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DevicesSelectedServiceTest {
  @NotNull
  private PersistentStateComponent myPersistentStateComponent;

  @NotNull
  private final RunManager myRunManager = Mockito.mock(RunManager.class);

  @NotNull
  private DevicesSelectedService myService;

  @NotNull
  private final RunnerAndConfigurationSettings myDefaultRunnerAndConfigurationSettings = mockConfigurationAndSettings("default config");

  @Before
  public void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2018-11-28T01:15:27Z"), ZoneId.of("America/Los_Angeles"));
    Project project = Mockito.mock(Project.class);
    Mockito.when(project.getService(RunManager.class)).thenReturn(myRunManager);
    myPersistentStateComponent = new PersistentStateComponent(project);
    myService = new DevicesSelectedService(myPersistentStateComponent, myRunManager, clock);
  }

  @Test
  public void getTargetSelectedWithComboBoxDevicesIsEmpty() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);

    List<Device> devices = Collections.emptyList();

    // Act
    Object target = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.empty(), target);
  }

  @Test
  public void getTargetSelectedWithComboBoxTargetSelectedWithDropDownIsNull() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);
    List<Device> devices = Collections.singletonList(TestDevices.buildPixel4Api30());

    // Act
    Object target = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.of(new QuickBootTarget(Keys.PIXEL_4_API_30)), target);
  }

  @Test
  public void getTargetSelectedWithComboBoxSelectedDeviceIsntPresent() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);

    myService.setTargetSelectedWithComboBox(new QuickBootTarget(Keys.PIXEL_3_API_30));

    List<Device> devices = Collections.singletonList(TestDevices.buildPixel4Api30());

    // Act
    Object target = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.of(new QuickBootTarget(Keys.PIXEL_4_API_30)), target);
  }

  @Test
  public void getTargetSelectedWithComboBoxConnectedDeviceIsntPresent() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);

    Target target = new ColdBootTarget(Keys.PIXEL_4_API_30);

    myService.setTargetSelectedWithComboBox(target);

    List<Device> devices = Collections.singletonList(TestDevices.buildPixel4Api30());

    // Act
    Object optionalTarget = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.of(target), optionalTarget);
  }

  @Test
  public void getTargetSelectedWithComboBoxTimeTargetWasSelectedWithDropDownIsBeforeConnectionTime() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);

    myService.setTargetSelectedWithComboBox(new QuickBootTarget(Keys.PIXEL_4_API_30));

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API 30")
      .setKey(Keys.PIXEL_3_API_30)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:28Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    var devices = List.of(TestDevices.buildPixel4Api30(), connectedDevice);

    // Act
    Object target = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.of(new RunningDeviceTarget(Keys.PIXEL_3_API_30)), target);
  }

  @Test
  public void getTargetSelectedWithComboBox() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);

    Target target = new ColdBootTarget(Keys.PIXEL_4_API_30);
    myService.setTargetSelectedWithComboBox(target);

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API 30")
      .setKey(Keys.PIXEL_3_API_30)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    var devices = List.of(TestDevices.buildPixel4Api30(), connectedDevice);

    // Act
    Object optionalTarget = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.of(target), optionalTarget);
  }

  /**
   * getTargetSelectedWithComboBox contains a statement that asserts that timeTargetWasSelectedWithDropDown isn't null. It failed in
   * scenarios involving setting the drop down target to a RunningDeviceTarget. This test verifies the fix.
   */
  @Test
  public void getTargetSelectedWithComboBoxTimeTargetWasSelectedWithDropDownAssertionDoesntFail() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);

    Target target = new RunningDeviceTarget(Keys.PIXEL_4_API_30);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(Keys.PIXEL_4_API_30)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Collections.singletonList(device);

    myService.setTargetSelectedWithComboBox(target);
    myService.setMultipleDevicesSelectedInComboBox(false);

    // Act
    Object optionalTarget = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.of(target), optionalTarget);
  }

  @Test
  public void setTargetSelectedWithComboBox() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);

    Target target2 = new QuickBootTarget(Keys.PIXEL_3_API_30);

    var devices = List.<Device>of(TestDevices.buildPixel4Api30(), TestDevices.buildPixel3Api30());

    // Act
    myService.setTargetSelectedWithComboBox(target2);

    // Assert
    assertEquals(Optional.of(target2), myService.getTargetSelectedWithComboBox(devices));

    // Act
    myService.setTargetSelectedWithComboBox(null);

    // Assert
    assertEquals(Optional.of(new QuickBootTarget(Keys.PIXEL_4_API_30)), myService.getTargetSelectedWithComboBox(devices));
  }

  @Test
  public void setMultipleDevicesSelectedInComboBox() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);

    // Act
    myService.setMultipleDevicesSelectedInComboBox(true);

    // Assert
    assertTrue(myService.isMultipleDevicesSelectedInComboBox());
  }

  @Test
  public void setTargetsSelectedWithDialog() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);

    Set<Target> targets = Collections.singleton(new QuickBootTarget(Keys.PIXEL_4_API_30));

    // Act
    myService.setTargetsSelectedWithDialog(targets);

    // Assert
    assertEquals(targets, myService.getTargetsSelectedWithDialog(Collections.emptyList()));
  }

  @Test
  public void targetStateTargetIsInstanceOfColdBootTarget() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);

    Set<Target> targets = Collections.singleton(new ColdBootTarget(Keys.PIXEL_4_API_30));

    // Act
    myService.setTargetsSelectedWithDialog(targets);

    // Assert
    assertEquals(targets, myService.getTargetsSelectedWithDialog(Collections.emptyList()));
  }

  @Test
  public void targetStateTargetIsInstanceOfBootWithSnapshotTarget() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);

    Set<Target> targets = Collections.singleton(new BootWithSnapshotTarget(Keys.PIXEL_4_API_30, Keys.PIXEL_4_API_30_SNAPSHOT_2));

    // Act
    myService.setTargetsSelectedWithDialog(targets);

    // Assert
    assertEquals(targets, myService.getTargetsSelectedWithDialog(Collections.emptyList()));
  }

  @Test
  public void keyStateKeyIsInstanceOfVirtualDeviceName() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);

    Set<Target> targets = Collections.singleton(new QuickBootTarget(new VirtualDeviceName("Pixel_4_API_30")));

    // Act
    myService.setTargetsSelectedWithDialog(targets);

    // Assert
    assertEquals(targets, myService.getTargetsSelectedWithDialog(Collections.emptyList()));
  }

  @Test
  public void keyStateKeyIsInstanceOfSerialNumber() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(myDefaultRunnerAndConfigurationSettings);

    Set<Target> targets = Collections.singleton(new RunningDeviceTarget(new SerialNumber("86UX00F4R")));

    // Act
    myService.setTargetsSelectedWithDialog(targets);

    // Assert
    assertEquals(Collections.emptySet(), myService.getTargetsSelectedWithDialog(Collections.emptyList()));
  }

  @Test
  public void selectedTargetWithComboBoxIsSavedByRunningConfiguration() {
    // Arrange
    RunnerAndConfigurationSettings phoneConfig = mockConfigurationAndSettings("phone config");
    RunnerAndConfigurationSettings wearConfig = mockConfigurationAndSettings("wear config");

    List<RunConfiguration> runConfigurations = List.of(phoneConfig.getConfiguration(), wearConfig.getConfiguration());
    Mockito.when(myRunManager.getAllConfigurationsList()).thenReturn(runConfigurations);

    Target phoneTarget = new RunningDeviceTarget(new SerialNumber("PHONE"));
    Target wearTarget = new RunningDeviceTarget(new SerialNumber("WEAR"));

    Device phoneDevice = new VirtualDevice.Builder()
      .setName("Phone")
      .setKey(phoneTarget.getDeviceKey())
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device wearDevice = new VirtualDevice.Builder()
      .setName("Wear")
      .setKey(wearTarget.getDeviceKey())
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = List.of(phoneDevice, wearDevice);

    // Act
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(phoneConfig);
    myService.setTargetSelectedWithComboBox(phoneTarget);

    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(wearConfig);
    myService.setTargetSelectedWithComboBox(wearTarget);

    // Assert
    assertEquals(Optional.of(wearTarget), myService.getTargetSelectedWithComboBox(devices));

    // Act
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(phoneConfig);

    // Assert
    assertEquals(Optional.of(phoneTarget), myService.getTargetSelectedWithComboBox(devices));
  }


  @Test
  public void selectedTargetWithComboBoxDoesNotPersistStateWhenSelectedConfigurationIsNull() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(null);

    Target phoneTarget = new RunningDeviceTarget(new SerialNumber("PHONE"));
    Target wearTarget = new RunningDeviceTarget(new SerialNumber("WEAR"));

    Device phoneDevice = new VirtualDevice.Builder()
      .setName("Phone")
      .setKey(phoneTarget.getDeviceKey())
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device wearDevice = new VirtualDevice.Builder()
      .setName("Wear")
      .setKey(wearTarget.getDeviceKey())
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = List.of(phoneDevice, wearDevice);

    // Act
    myService.setTargetSelectedWithComboBox(wearTarget);

    // Assert
    // The wearTarget state is not persisted, we should default to the first of the devices
    assertEquals(Optional.of(phoneTarget), myService.getTargetSelectedWithComboBox(devices));
    assertPersistentStateComponentCanBeSerializedAndDeserialized();
  }

  @Test
  public void selectedTargetWithDialogIsSavedByRunningConfiguration() {
    // Arrange
    RunnerAndConfigurationSettings phoneConfig = mockConfigurationAndSettings("phone config");
    RunnerAndConfigurationSettings wearConfig = mockConfigurationAndSettings("wear config");

    List<RunConfiguration> runConfigurations = List.of(phoneConfig.getConfiguration(), wearConfig.getConfiguration());
    Mockito.when(myRunManager.getAllConfigurationsList()).thenReturn(runConfigurations);

    Device phoneDevice = new VirtualDevice.Builder()
      .setName("Phone")
      .setKey(new SerialNumber("PHONE"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device wearDevice = new VirtualDevice.Builder()
      .setName("Wear")
      .setKey(new SerialNumber("WEAR"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = List.of(phoneDevice, wearDevice);

    Set<Target> phoneTargets = Set.of(new RunningDeviceTarget(phoneDevice.key()));
    Set<Target> wearTargets = Set.of(new RunningDeviceTarget(wearDevice.key()));

    // Act
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(phoneConfig);
    myService.setTargetsSelectedWithDialog(phoneTargets);

    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(wearConfig);
    myService.setTargetsSelectedWithDialog(wearTargets);

    // Assert
    assertEquals(wearTargets, myService.getTargetsSelectedWithDialog(devices));

    // Act
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(phoneConfig);

    // Assert
    assertEquals(phoneTargets, myService.getTargetsSelectedWithDialog(devices));
    assertPersistentStateComponentCanBeSerializedAndDeserialized();
  }

  @Test
  public void selectedTargetWithDialogDoesNotPersistStateWhenSelectedConfigurationIsNull() {
    // Arrange
    Mockito.when(myRunManager.getSelectedConfiguration()).thenReturn(null);

    Device phoneDevice = new VirtualDevice.Builder()
      .setName("Phone")
      .setKey(new SerialNumber("PHONE"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device wearDevice = new VirtualDevice.Builder()
      .setName("Wear")
      .setKey(new SerialNumber("WEAR"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = List.of(phoneDevice, wearDevice);
    Set<Target> wearTargets = Set.of(new RunningDeviceTarget(wearDevice.key()));

    // Act
    myService.setTargetsSelectedWithDialog(wearTargets);

    // Assert
    // The dialog selection should not be persisted, so we expect an empty selection
    assertEquals(Set.of(), myService.getTargetsSelectedWithDialog(devices));
    assertPersistentStateComponentCanBeSerializedAndDeserialized();
  }

  private void assertPersistentStateComponentCanBeSerializedAndDeserialized() {
    XmlSerializer.deserialize(XmlSerializer.serialize(myPersistentStateComponent.getState()), MapState.class);
  }

  @NotNull
  private static RunnerAndConfigurationSettings mockConfigurationAndSettings(@NotNull String name) {
    var runConfiguration = Mockito.mock(AndroidRunConfiguration.class);
    Mockito.when(runConfiguration.getName()).thenReturn(name);
    Mockito.when(runConfiguration.getUserData(DeployableToDevice.getKEY())).thenReturn(true);
    var configurationAndSettings = Mockito.mock(RunnerAndConfigurationSettings.class);
    Mockito.when(configurationAndSettings.getConfiguration()).thenReturn(runConfiguration);
    return configurationAndSettings;
  }
}
