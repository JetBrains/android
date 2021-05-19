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
package com.android.tools.idea.devicemanager.physicaltab;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice.ConnectionType;
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
  @Test
  public void newPhysicalDeviceTableModel() {
    // Arrange
    PhysicalDevice onlinePixel5 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("0A071FDD4003ZG"))
      .setLastOnlineTime(Instant.parse("2021-03-24T22:38:05.890570Z"))
      .setName("Google Pixel 5")
      .setTarget("Android 11.0")
      .setApi("30")
      .addConnectionType(ConnectionType.USB)
      .build();

    List<PhysicalDevice> devices = Arrays.asList(TestPhysicalDevices.GOOGLE_PIXEL_3, onlinePixel5);

    // Act
    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(devices);

    // Assert
    assertEquals(Arrays.asList(onlinePixel5, TestPhysicalDevices.GOOGLE_PIXEL_3), model.getDevices());
  }

  @Test
  public void addOrSetModelRowIndexEqualsNegativeOne() {
    // Arrange
    TableModelListener listener = Mockito.mock(TableModelListener.class);

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(Lists.newArrayList(TestPhysicalDevices.GOOGLE_PIXEL_3));
    model.addTableModelListener(listener);

    PhysicalDevice onlinePixel5 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("0A071FDD4003ZG"))
      .setName("Google Pixel 5")
      .setTarget("Android 11.0")
      .setApi("30")
      .addConnectionType(ConnectionType.USB)
      .build();

    // Act
    model.addOrSet(onlinePixel5);

    // Assert
    assertEquals(Arrays.asList(onlinePixel5, TestPhysicalDevices.GOOGLE_PIXEL_3), model.getDevices());
    Mockito.verify(listener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model))));
  }

  @Test
  public void addOrSetModelRowIndexDoesntEqualNegativeOne() {
    // Arrange
    TableModelListener listener = Mockito.mock(TableModelListener.class);
    List<PhysicalDevice> devices = Arrays.asList(TestPhysicalDevices.GOOGLE_PIXEL_3, TestPhysicalDevices.GOOGLE_PIXEL_5);

    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(devices);
    model.addTableModelListener(listener);

    PhysicalDevice onlinePixel5 = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("0A071FDD4003ZG"))
      .setName("Google Pixel 5")
      .setTarget("Android 11.0")
      .setApi("30")
      .addConnectionType(ConnectionType.USB)
      .build();

    // Act
    model.addOrSet(onlinePixel5);

    // Assert
    assertEquals(Arrays.asList(onlinePixel5, TestPhysicalDevices.GOOGLE_PIXEL_3), model.getDevices());
    Mockito.verify(listener).tableChanged(ArgumentMatchers.argThat(new TableModelEventArgumentMatcher(new TableModelEvent(model))));
  }

  @Test
  public void getRowCount() {
    // Arrange
    TableModel model = new PhysicalDeviceTableModel(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3));

    // Act
    int count = model.getRowCount();

    // Assert
    assertEquals(1, count);
  }

  @Test
  public void getValueAt() {
    // Arrange
    TableModel model = new PhysicalDeviceTableModel(Collections.singletonList(TestPhysicalDevices.GOOGLE_PIXEL_3));

    // Act
    Object value = model.getValueAt(0, 0);

    // Assert
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3, value);
  }
}
