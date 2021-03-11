/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.idea.run.LaunchCompatibility;
import com.android.tools.idea.run.LaunchCompatibility.State;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import java.nio.file.FileSystem;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import javax.swing.table.TableModel;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class SelectMultipleDevicesDialogTableTest {
  private SelectMultipleDevicesDialogTable myTable;

  @Before
  public void initTable() {
    myTable = new SelectMultipleDevicesDialogTable();
  }

  @Test
  public void getSelectedTargets() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    myTable.setModel(new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device), () -> false));

    // Act
    myTable.setSelected(true, 0);

    // Assert
    assertEquals(Collections.singleton(new QuickBootTarget(new VirtualDeviceName("Pixel_3_API_29"))), myTable.getSelectedTargets());
  }

  @Test
  public void setSelectedTargets() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    myTable.setModel(new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device), () -> false));
    Set<Target> targets = Collections.singleton(new QuickBootTarget(new VirtualDeviceName("Pixel_3_API_29")));

    // Act
    myTable.setSelectedTargets(targets);

    // Assert
    assertTrue(myTable.isSelected(0));
  }

  @Test
  public void setModelDoesntThrowAssertionErrorWhenColumnCountEqualsZero() {
    // Arrange
    TableModel model = new SelectMultipleDevicesDialogTableModel(Collections.emptyList(), () -> false);

    // Act
    myTable.setModel(model);
  }

  @Test
  public void setModel() {
    // Arrange
    Device device = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey(new SerialNumber("00fff9d2279fa601"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    TableModel model = new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device), () -> false);

    // Act
    myTable.setModel(model);

    // Assert
    // @formatter:off
    Object data = Arrays.asList(
      Arrays.asList("",    "Type",           "Device"),
      Arrays.asList(false, device.getIcon(), "LGE Nexus 5X"));
    // @formatter:on

    assertEquals(data, myTable.getData());
  }

  @Test
  public void setModelIssue() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 29")
      .setLaunchCompatibility(new LaunchCompatibility(State.ERROR, "Missing system image"))
      .setKey(new VirtualDeviceName("Pixel_4_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    TableModel model = new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device), () -> false);

    // Act
    myTable.setModel(model);

    // Assert
    // @formatter:off
    Object data = Arrays.asList(
      Arrays.asList("",    "Type",           "Device"),
      Arrays.asList(false, device.getIcon(), "<html>Pixel 4 API 29<br>Missing system image"));
    // @formatter:on

    assertEquals(data, myTable.getData());
  }

  @Test
  public void setModelSerialNumber() {
    // Arrange
    Device device1 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey(new SerialNumber("00fff9d2279fa601"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    Device device2 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey(new SerialNumber("00fff9d2279fa602"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .build();

    TableModel model = new SelectMultipleDevicesDialogTableModel(Arrays.asList(device1, device2), () -> false);

    // Act
    myTable.setModel(model);

    // Assert
    // @formatter:off
    Object data = Arrays.asList(
      Arrays.asList("",    "Type",            "Device",          "Serial Number"),
      Arrays.asList(false, device1.getIcon(), "LGE Nexus 5X", "00fff9d2279fa601"),
      Arrays.asList(false, device2.getIcon(), "LGE Nexus 5X", "00fff9d2279fa602"));
    // @formatter:on

    assertEquals(data, myTable.getData());
  }

  @Test
  public void setModelBootOption() {
    // Arrange
    FileSystem fileSystem = Jimfs.newFileSystem(Configuration.unix());

    Device device = new VirtualDevice.Builder()
      .setName("Pixel 4 API 30")
      .setKey(new VirtualDevicePath("/home/user/.android/avd/Pixel_4_API_30.avd"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setType(Device.Type.PHONE)
      .addSnapshot(new Snapshot(fileSystem.getPath("/home/user/.android/avd/Pixel_4_API_30.avd/snapshots/snap_2020-12-07_16-36-58")))
      .setSelectDeviceSnapshotComboBoxSnapshotsEnabled(true)
      .build();

    TableModel model = new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device), () -> true);

    // Act
    myTable.setModel(model);

    // Assert
    Object icon = device.getIcon();

    // @formatter:off
    Object data = Arrays.asList(
      Arrays.asList("",    "Type", "Device",         "Boot Option"),
      Arrays.asList(false, icon,   "Pixel 4 API 30", "Cold Boot"),
      Arrays.asList(false, icon,   "Pixel 4 API 30", "Quick Boot"),
      Arrays.asList(false, icon,   "Pixel 4 API 30", "snap_2020-12-07_16-36-58"));
    // @formatter:on

    assertEquals(data, myTable.getData());
  }
}
