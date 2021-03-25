/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.physicaltab;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.testing.swing.TableModelEventArgumentMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDeviceTableModelTest {
  private static final @NotNull PhysicalDevice CONNECTED_PIXEL_3 = PhysicalDevice.newConnectedDevice("86UX00F4R");

  @Test
  public void handleConnectedDeviceDevicesContainsDisconnectedPixel3() {
    // Arrange
    PhysicalDevice disconnectedPixel5 = PhysicalDevice.newDisconnectedDevice("0A071FDD4003ZG");

    List<PhysicalDevice> devices = new ArrayList<>(2);

    devices.add(disconnectedPixel5);
    devices.add(PhysicalDevice.newDisconnectedDevice("86UX00F4R"));

    TableModelListener listener = Mockito.mock(TableModelListener.class);

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(devices);
    model.addTableModelListener(listener);

    // Act
    model.handleConnectedDevice(CONNECTED_PIXEL_3);

    // Assert
    assertEquals(Arrays.asList(disconnectedPixel5, CONNECTED_PIXEL_3), model.getDevices());
    Mockito.verify(listener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model, 1))));
  }

  @Test
  public void handleConnectedDevice() {
    // Arrange
    TableModelListener listener = Mockito.mock(TableModelListener.class);

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(new ArrayList<>(1));
    model.addTableModelListener(listener);

    // Act
    model.handleConnectedDevice(CONNECTED_PIXEL_3);

    // Assert
    assertEquals(Collections.singletonList(CONNECTED_PIXEL_3), model.getDevices());

    TableModelEvent event = new TableModelEvent(model, 0, 0, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT);
    Mockito.verify(listener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(event)));
  }

  @Test
  public void handleDisconnectedDevice() {
    // Arrange
    PhysicalDevice disconnectedPixel5 = PhysicalDevice.newDisconnectedDevice("0A071FDD4003ZG");

    List<PhysicalDevice> devices = new ArrayList<>(2);

    devices.add(disconnectedPixel5);
    devices.add(CONNECTED_PIXEL_3);

    TableModelListener listener = Mockito.mock(TableModelListener.class);

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(devices);
    model.addTableModelListener(listener);

    PhysicalDevice disconnectedPixel3 = PhysicalDevice.newDisconnectedDevice("86UX00F4R");

    // Act
    model.handleDisconnectedDevice(disconnectedPixel3);

    // Assert
    assertEquals(Arrays.asList(disconnectedPixel5, disconnectedPixel3), model.getDevices());
    Mockito.verify(listener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model, 1))));
  }

  @Test
  public void getRowCount() {
    // Arrange
    TableModel model = new PhysicalDeviceTableModel(Collections.singletonList(CONNECTED_PIXEL_3));

    // Act
    int count = model.getRowCount();

    // Assert
    assertEquals(1, count);
  }

  @Test
  public void getValueAt() {
    // Arrange
    TableModel model = new PhysicalDeviceTableModel(Collections.singletonList(CONNECTED_PIXEL_3));

    // Act
    Object value = model.getValueAt(0, 0);

    // Assert
    assertEquals(CONNECTED_PIXEL_3, value);
  }
}
