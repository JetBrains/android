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

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.devicemanager.DeviceTableCellRenderer;
import com.android.tools.idea.devicemanager.SerialNumber;
import com.intellij.ui.table.JBTable;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PhysicalDeviceTableCellRendererTest {
  private final @NotNull DeviceTableCellRenderer<PhysicalDevice> myRenderer = new PhysicalDeviceTableCellRenderer();
  private final @NotNull JTable myTable = new JBTable();

  @Test
  public void getNameOverrideIsntEmpty() {
    // Arrange
    Object device = new PhysicalDevice.Builder()
      .setKey(new SerialNumber("86UX00F4R"))
      .setName("Google Pixel 3")
      .setNameOverride("Name Override")
      .setTarget("Android 12.0")
      .setAndroidVersion(new AndroidVersion(31))
      .build();

    // Act
    myRenderer.getTableCellRendererComponent(myTable, device, false, true, 0, 0);

    // Assert
    assertEquals("Name Override", myRenderer.getNameLabel().getText());
  }

  @Test
  public void getName() {
    // Act
    myRenderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, false, true, 0, 0);

    // Assert
    assertEquals("Google Pixel 3", myRenderer.getNameLabel().getText());
  }
}
