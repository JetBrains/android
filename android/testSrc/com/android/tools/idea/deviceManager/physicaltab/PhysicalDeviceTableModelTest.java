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

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.table.TableModel;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PhysicalDeviceTableModelTest {
  private static final @NotNull PhysicalDevice OFFLINE_PIXEL_3 = new PhysicalDevice.Builder()
    .setSerialNumber("86UX00F4R")
    .build();

  @Test
  public void newPhysicalDeviceTableModel() {
    // Arrange
    PhysicalDevice onlinePixel5 = new PhysicalDevice.Builder()
      .setSerialNumber("0A071FDD4003ZG")
      .setLastOnlineTime(Instant.parse("2021-03-24T22:38:05.890570Z"))
      .setOnline(true)
      .build();

    List<PhysicalDevice> devices = Arrays.asList(OFFLINE_PIXEL_3, onlinePixel5);

    // Act
    PhysicalDeviceTableModel model = new PhysicalDeviceTableModel(devices);

    // Assert
    assertEquals(Arrays.asList(onlinePixel5, OFFLINE_PIXEL_3), model.getDevices());
  }

  @Test
  public void getRowCount() {
    // Arrange
    TableModel model = new PhysicalDeviceTableModel(Collections.singletonList(OFFLINE_PIXEL_3));

    // Act
    int count = model.getRowCount();

    // Assert
    assertEquals(1, count);
  }

  @Test
  public void getValueAt() {
    // Arrange
    TableModel model = new PhysicalDeviceTableModel(Collections.singletonList(OFFLINE_PIXEL_3));

    // Act
    Object value = model.getValueAt(0, 0);

    // Assert
    assertEquals(OFFLINE_PIXEL_3, value);
  }
}
