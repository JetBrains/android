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
import com.google.common.collect.Lists;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDeviceTableModelTest {
  private static final PhysicalDevice DISCONNECTED_PIXEL_3 = new PhysicalDevice("86UX00F4R");

  @Test
  public void newPhysicalDeviceTableModel() {
    // Arrange
    PhysicalDevice connectedPixel5 = new PhysicalDevice("0A071FDD4003ZG", Instant.parse("2021-03-24T22:38:05.890570Z"));
    List<PhysicalDevice> devices = Arrays.asList(DISCONNECTED_PIXEL_3, connectedPixel5);

    // Act
    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(devices);

    // Assert
    assertEquals(Arrays.asList(connectedPixel5, DISCONNECTED_PIXEL_3), model.getDevices());
  }

  @Test
  public void handleConnectedDeviceModelRowIndexEqualsNegativeOne() {
    // Arrange
    TableModelListener listener = Mockito.mock(TableModelListener.class);

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Lists.newArrayList(DISCONNECTED_PIXEL_3));
    model.addTableModelListener(listener);

    PhysicalDevice connectedPixel5 = new PhysicalDevice("0A071FDD4003ZG", Instant.parse("2021-03-24T22:38:05.890570Z"));

    // Act
    model.handleConnectedDevice(connectedPixel5);

    // Assert
    assertEquals(Arrays.asList(connectedPixel5, DISCONNECTED_PIXEL_3), model.getDevices());
    Mockito.verify(listener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model))));
  }

  @Test
  public void handleConnectedDeviceModelRowIndexDoesntEqualNegativeOne() {
    // Arrange
    TableModelListener listener = Mockito.mock(TableModelListener.class);
    PhysicalDevice disconnectedPixel5 = new PhysicalDevice("0A071FDD4003ZG");

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Arrays.asList(DISCONNECTED_PIXEL_3, disconnectedPixel5));
    model.addTableModelListener(listener);

    PhysicalDevice connectedPixel5 = new PhysicalDevice("0A071FDD4003ZG", Instant.parse("2021-03-24T22:38:05.890570Z"));

    // Act
    model.handleConnectedDevice(connectedPixel5);

    // Assert
    assertEquals(Arrays.asList(connectedPixel5, DISCONNECTED_PIXEL_3), model.getDevices());
    Mockito.verify(listener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model))));
  }

  @Test
  public void handleDisconnectedDevice() {
    // Arrange
    Instant connectionTime = Instant.parse("2021-03-24T22:38:05.890570Z");

    PhysicalDevice connectedPixel3 = new PhysicalDevice("86UX00F4R", connectionTime);
    PhysicalDevice connectedPixel5 = new PhysicalDevice("0A071FDD4003ZG", connectionTime);

    TableModelListener listener = Mockito.mock(TableModelListener.class);

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Arrays.asList(connectedPixel3, connectedPixel5));
    model.addTableModelListener(listener);

    // Act
    model.handleDisconnectedDevice(DISCONNECTED_PIXEL_3);

    // Assert
    assertEquals(Arrays.asList(connectedPixel5, DISCONNECTED_PIXEL_3), model.getDevices());
    Mockito.verify(listener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model))));
  }

  @Test
  public void getRowCount() {
    // Arrange
    TableModel model = new PhysicalDeviceTableModel(Collections.singletonList(DISCONNECTED_PIXEL_3));

    // Act
    int count = model.getRowCount();

    // Assert
    assertEquals(1, count);
  }

  @Test
  public void getValueAt() {
    // Arrange
    TableModel model = new PhysicalDeviceTableModel(Collections.singletonList(DISCONNECTED_PIXEL_3));

    // Act
    Object value = model.getValueAt(0, 0);

    // Assert
    assertEquals(DISCONNECTED_PIXEL_3, value);
  }
}
