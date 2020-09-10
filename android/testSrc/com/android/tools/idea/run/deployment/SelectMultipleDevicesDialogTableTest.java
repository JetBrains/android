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
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
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
  public void getSelectedDevices() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    myTable.setModel(new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device)));

    // Act
    myTable.setSelected(true, 0);

    // Assert
    assertEquals(Collections.singletonList(device), myTable.getSelectedDevices());
  }

  @Test
  public void setSelectedDevices() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new VirtualDeviceName("Pixel_3_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    myTable.setModel(new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device)));

    // Act
    myTable.setSelectedDevices(Collections.singleton(new VirtualDeviceName("Pixel_3_API_29")));

    // Assert
    assertTrue(myTable.isSelected(0));
  }

  @Test
  public void setModelDoesntThrowAssertionErrorWhenColumnCountEqualsZero() {
    // Arrange
    TableModel model = new SelectMultipleDevicesDialogTableModel(Collections.emptyList());

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
      .build();

    TableModel model = new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device));

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
      .setValidityReason("Missing system image")
      .setKey(new VirtualDeviceName("Pixel_4_API_29"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    TableModel model = new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device));

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
      .build();

    Device device2 = new PhysicalDevice.Builder()
      .setName("LGE Nexus 5X")
      .setKey(new SerialNumber("00fff9d2279fa602"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .build();

    TableModel model = new SelectMultipleDevicesDialogTableModel(Arrays.asList(device1, device2));

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
  public void setModelDefaultSnapshot() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new NonprefixedKey("Pixel_3_API_29/default_boot"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshot(Snapshot.quickboot(FileSystems.getDefault()))
      .build();

    TableModel model = new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device));

    // Act
    myTable.setModel(model);

    // Assert
    // @formatter:off
    Object data = Arrays.asList(
      Arrays.asList("",    "Type",           "Device",         "Snapshot"),
      Arrays.asList(false, device.getIcon(), "Pixel 3 API 29", "Quickboot"));
    // @formatter:on

    assertEquals(data, myTable.getData());
  }

  @Test
  public void setModelNondefaultSnapshot() {
    // Arrange
    Device device = new VirtualDevice.Builder()
      .setName("Pixel 3 API 29")
      .setKey(new NonprefixedKey("Pixel_3_API_29/snap_2019-09-27_15-48-09"))
      .setAndroidDevice(Mockito.mock(AndroidDevice.class))
      .setSnapshot(new Snapshot(Paths.get("snap_2019-09-27_15-48-09"), "Snapshot"))
      .build();

    TableModel model = new SelectMultipleDevicesDialogTableModel(Collections.singletonList(device));

    // Act
    myTable.setModel(model);

    // Assert
    // @formatter:off
    Object data = Arrays.asList(
      Arrays.asList("",    "Type",           "Device",         "Snapshot"),
      Arrays.asList(false, device.getIcon(), "Pixel 3 API 29", "Snapshot"));
    // @formatter:on

    assertEquals(data, myTable.getData());
  }
}
