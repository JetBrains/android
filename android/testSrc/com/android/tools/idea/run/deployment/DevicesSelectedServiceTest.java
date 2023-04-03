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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.run.AndroidDevice;
import com.android.tools.idea.run.deployment.DevicesSelectedService.PersistentStateComponent;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DevicesSelectedServiceTest {
  private final @NotNull DevicesSelectedService myService;

  public DevicesSelectedServiceTest() {
    Clock clock = Clock.fixed(Instant.parse("2018-11-28T01:15:27Z"), ZoneId.of("America/Los_Angeles"));
    myService = new DevicesSelectedService(new PersistentStateComponent(), clock);
  }

  @Test
  public void getTargetSelectedWithComboBoxDevicesIsEmpty() {
    // Arrange
    List<Device> devices = Collections.emptyList();

    // Act
    Object target = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.empty(), target);
  }

  @Test
  public void getTargetSelectedWithComboBoxTargetSelectedWithDropDownIsNull() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(Keys.PIXEL_4_API_30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Collections.singletonList(device);

    // Act
    Object target = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.of(new QuickBootTarget(Keys.PIXEL_4_API_30)), target);
  }

  @Test
  public void getTargetSelectedWithComboBoxSelectedDeviceIsntPresent() {
    // Arrange
    myService.setTargetSelectedWithComboBox(new QuickBootTarget(Keys.PIXEL_3_API_30));

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(Keys.PIXEL_4_API_30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Collections.singletonList(device);

    // Act
    Object target = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.of(new QuickBootTarget(Keys.PIXEL_4_API_30)), target);
  }

  @Test
  public void getTargetSelectedWithComboBoxConnectedDeviceIsntPresent() {
    // Arrange
    Target target = new ColdBootTarget(Keys.PIXEL_4_API_30);

    myService.setTargetSelectedWithComboBox(target);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(Keys.PIXEL_4_API_30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Collections.singletonList(device);

    // Act
    Object optionalTarget = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.of(target), optionalTarget);
  }

  @Test
  public void getTargetSelectedWithComboBoxTimeTargetWasSelectedWithDropDownIsBeforeConnectionTime() {
    // Arrange
    myService.setTargetSelectedWithComboBox(new QuickBootTarget(Keys.PIXEL_4_API_30));

    Device disconnectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(Keys.PIXEL_4_API_30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API 30")
      .setKey(Keys.PIXEL_3_API_30)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:28Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Arrays.asList(disconnectedDevice, connectedDevice);

    // Act
    Object target = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.of(new RunningDeviceTarget(Keys.PIXEL_3_API_30)), target);
  }

  @Test
  public void getTargetSelectedWithComboBox() {
    // Arrange
    Target target = new ColdBootTarget(Keys.PIXEL_4_API_30);
    myService.setTargetSelectedWithComboBox(target);

    Device disconnectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(Keys.PIXEL_4_API_30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API 30")
      .setKey(Keys.PIXEL_3_API_30)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Arrays.asList(disconnectedDevice, connectedDevice);

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
    Target target2 = new QuickBootTarget(Keys.PIXEL_3_API_30);

    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(Keys.PIXEL_4_API_30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 30")
      .setKey(Keys.PIXEL_3_API_30)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Arrays.asList(device1, device2);

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
    // Act
    myService.setMultipleDevicesSelectedInComboBox(true);

    // Assert
    assertTrue(myService.isMultipleDevicesSelectedInComboBox());
  }

  @Test
  public void setTargetsSelectedWithDialog() {
    // Arrange
    Set<Target> targets = Collections.singleton(new QuickBootTarget(Keys.PIXEL_4_API_30));

    // Act
    myService.setTargetsSelectedWithDialog(targets);

    // Assert
    assertEquals(targets, myService.getTargetsSelectedWithDialog(Collections.emptyList()));
  }

  @Test
  public void targetStateTargetIsInstanceOfColdBootTarget() {
    // Arrange
    Set<Target> targets = Collections.singleton(new ColdBootTarget(Keys.PIXEL_4_API_30));

    // Act
    myService.setTargetsSelectedWithDialog(targets);

    // Assert
    assertEquals(targets, myService.getTargetsSelectedWithDialog(Collections.emptyList()));
  }

  @Test
  public void targetStateTargetIsInstanceOfBootWithSnapshotTarget() {
    // Arrange
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());
    Path snapshotKey = fileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-17_12-26-30");

    Set<Target> targets = Collections.singleton(new BootWithSnapshotTarget(Keys.PIXEL_4_API_30, snapshotKey));

    // Act
    myService.setTargetsSelectedWithDialog(targets);

    // Assert
    assertEquals(targets, myService.getTargetsSelectedWithDialog(Collections.emptyList()));
  }

  @Test
  public void keyStateKeyIsInstanceOfVirtualDeviceName() {
    // Arrange
    Set<Target> targets = Collections.singleton(new QuickBootTarget(new VirtualDeviceName("Pixel_4_API_30")));

    // Act
    myService.setTargetsSelectedWithDialog(targets);

    // Assert
    assertEquals(targets, myService.getTargetsSelectedWithDialog(Collections.emptyList()));
  }

  @Test
  public void keyStateKeyIsInstanceOfSerialNumber() {
    // Arrange
    Set<Target> targets = Collections.singleton(new RunningDeviceTarget(new SerialNumber("86UX00F4R")));

    // Act
    myService.setTargetsSelectedWithDialog(targets);

    // Assert
    assertEquals(Collections.emptySet(), myService.getTargetsSelectedWithDialog(Collections.emptyList()));
  }
}
