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
    myService = new DevicesSelectedService(new PersistentStateComponent(), clock, () -> false);
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
    Key key = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Collections.singletonList(device);

    // Act
    Object target = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.of(new QuickBootTarget(key)), target);
  }

  @Test
  public void getTargetSelectedWithComboBoxSelectedDeviceIsntPresent() {
    // Arrange
    myService.setTargetSelectedWithComboBox(new QuickBootTarget(new VirtualDevicePath("/home/user/.android/avd/Pixel_3_API_30.avd")));

    Key key = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Collections.singletonList(device);

    // Act
    Object target = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.of(new QuickBootTarget(key)), target);
  }

  @Test
  public void getTargetSelectedWithComboBoxConnectedDeviceIsntPresent() {
    // Arrange
    Key key = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");
    Target target = new ColdBootTarget(key);

    myService.setTargetSelectedWithComboBox(target);

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(key)
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
    Key disconnectedDeviceKey = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");

    myService.setTargetSelectedWithComboBox(new QuickBootTarget(disconnectedDeviceKey));

    Device disconnectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(disconnectedDeviceKey)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Key connectedDeviceKey = new VirtualDevicePath("/home/user/.android/avd/Pixel_3_API_30.avd");

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API 30")
      .setKey(connectedDeviceKey)
      .setConnectionTime(Instant.parse("2018-11-28T01:15:28Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Arrays.asList(disconnectedDevice, connectedDevice);

    // Act
    Object target = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    // TODO(http://b/179836372)
    // assertEquals(Optional.of(new RunningDeviceTarget(connectedDeviceKey)), target);

    assertEquals(Optional.of(new QuickBootTarget(connectedDeviceKey)), target);
  }

  @Test
  public void getTargetSelectedWithComboBox() {
    // Arrange
    Key key = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");
    Target target = new ColdBootTarget(key);

    myService.setTargetSelectedWithComboBox(target);

    Device disconnectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(key)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device connectedDevice = new VirtualDevice.Builder()
      .setName("Pixel 3 API 30")
      .setKey(new VirtualDevicePath("/home/user/.android/avd/Pixel_3_API_30.avd"))
      .setConnectionTime(Instant.parse("2018-11-28T01:15:27Z"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    List<Device> devices = Arrays.asList(disconnectedDevice, connectedDevice);

    // Act
    Object optionalTarget = myService.getTargetSelectedWithComboBox(devices);

    // Assert
    assertEquals(Optional.of(target), optionalTarget);
  }

  @Test
  public void setTargetSelectedWithComboBox() {
    // Arrange
    Key key2 = new VirtualDevicePath("/home/user/.android/avd/Pixel_3_API_30.avd");
    Target target2 = new QuickBootTarget(key2);

    Key key1 = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");

    Device device1 = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(key1)
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    Device device2 = new VirtualDevice.Builder()
      .setName("Pixel 3 API 30")
      .setKey(key2)
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
    assertEquals(Optional.of(new QuickBootTarget(key1)), myService.getTargetSelectedWithComboBox(devices));
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
    Set<Target> targets = Collections.singleton(new QuickBootTarget(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd")));

    // Act
    myService.setTargetsSelectedWithDialog(targets);

    // Assert
    assertEquals(targets, myService.getTargetsSelectedWithDialog(Collections.emptyList()));
  }

  @Test
  public void targetStateTargetIsInstanceOfColdBootTarget() {
    // Arrange
    Set<Target> targets = Collections.singleton(new ColdBootTarget(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd")));

    // Act
    myService.setTargetsSelectedWithDialog(targets);

    // Assert
    assertEquals(targets, myService.getTargetsSelectedWithDialog(Collections.emptyList()));
  }

  @Test
  public void targetStateTargetIsInstanceOfBootWithSnapshotTarget() {
    // Arrange
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());

    Key deviceKey = new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd");
    Path snapshotKey = fileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-17_12-26-30");

    Set<Target> targets = Collections.singleton(new BootWithSnapshotTarget(deviceKey, snapshotKey));

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
    assertEquals(targets, myService.getTargetsSelectedWithDialog(Collections.emptyList()));
  }
}
