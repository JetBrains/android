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
package com.android.tools.idea.devicemanager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.idea.devicemanager.physicaltab.PhysicalDevice;
import com.android.tools.idea.devicemanager.physicaltab.TestPhysicalDevices;
import com.intellij.ide.ui.laf.darcula.DarculaTableSelectedCellHighlightBorder;
import com.intellij.ui.table.JBTable;
import icons.StudioIcons;
import java.util.function.Function;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.BorderUIResource.EmptyBorderUIResource;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class DeviceTableCellRendererTest {
  private final JTable myTable = new JBTable();

  @Test
  public void getTableCellRendererComponentDeviceIsOnline() {
    // Arrange
    Function<Object, Border> getBorder = mockGetBorder("Table.cellNoFocusBorder", new EmptyBorderUIResource(2, 3, 2, 3));
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, getBorder);

    Device device = new PhysicalDevice.Builder()
      .setSerialNumber("86UX00F4R")
      .setName("Google Pixel 3")
      .setOnline(true)
      .setTarget("Android 12 Preview")
      .setApi("S")
      .build();

    // Act
    renderer.getTableCellRendererComponent(myTable, device, false, false, 0, 0);

    // Assert
    assertEquals(device.getIcon(), renderer.getIconLabel().getIcon());
    assertEquals(device.getName(), renderer.getNameLabel().getText());
    assertEquals(StudioIcons.Common.CIRCLE_GREEN, renderer.getOnlineLabel().getIcon());
    assertEquals(device.getTarget(), renderer.getLine2Label().getText());
  }

  @Test
  public void getTableCellRendererComponent() {
    // Arrange
    Function<Object, Border> getBorder = mockGetBorder("Table.cellNoFocusBorder", new EmptyBorderUIResource(2, 3, 2, 3));
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, getBorder);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, false, false, 0, 0);

    // Assert
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3.getIcon(), renderer.getIconLabel().getIcon());
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3.getName(), renderer.getNameLabel().getText());
    assertNull(renderer.getOnlineLabel().getIcon());
    assertEquals(TestPhysicalDevices.GOOGLE_PIXEL_3.getTarget(), renderer.getLine2Label().getText());
  }

  @Test
  public void getBackgroundSelected() {
    // Arrange
    Function<Object, Border> getBorder = mockGetBorder("Table.cellNoFocusBorder", new EmptyBorderUIResource(2, 3, 2, 3));
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, getBorder);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, true, false, 0, 0);

    // Assert
    assertEquals(myTable.getSelectionBackground(), renderer.getPanel().getBackground());
  }

  @Test
  public void getBackground() {
    // Arrange
    Function<Object, Border> getBorder = mockGetBorder("Table.cellNoFocusBorder", new EmptyBorderUIResource(2, 3, 2, 3));
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, getBorder);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, false, false, 0, 0);

    // Assert
    assertEquals(myTable.getBackground(), renderer.getPanel().getBackground());
  }

  @Test
  public void getBorderUnfocused() {
    // Arrange
    Border border = new EmptyBorderUIResource(2, 3, 2, 3);

    Function<Object, Border> getBorder = mockGetBorder("Table.cellNoFocusBorder", border);
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, getBorder);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, false, false, 0, 0);

    // Assert
    assertEquals(border, renderer.getPanel().getBorder());
  }

  @Test
  public void getBorderSelected() {
    // Arrange
    Border border = new DarculaTableSelectedCellHighlightBorder();

    Function<Object, Border> getBorder = mockGetBorder("Table.focusSelectedCellHighlightBorder", border);
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, getBorder);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, true, true, 0, 0);

    // Assert
    assertEquals(border, renderer.getPanel().getBorder());
  }

  @Test
  public void getBorder() {
    // Arrange
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, false, true, 0, 0);

    // Assert
    assertEquals(UIManager.getBorder("Table.focusCellHighlightBorder"), renderer.getPanel().getBorder());
  }

  @Test
  public void getForegroundSelected() {
    // Arrange
    Function<Object, Border> getBorder = mockGetBorder("Table.cellNoFocusBorder", new EmptyBorderUIResource(2, 3, 2, 3));
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, getBorder);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, true, false, 0, 0);

    // Assert
    assertEquals(myTable.getSelectionForeground(), renderer.getNameLabel().getForeground());
  }

  @Test
  public void getForeground() {
    // Arrange
    Function<Object, Border> getBorder = mockGetBorder("Table.cellNoFocusBorder", new EmptyBorderUIResource(2, 3, 2, 3));
    DeviceTableCellRenderer<Device> renderer = new DeviceTableCellRenderer<>(Device.class, getBorder);

    // Act
    renderer.getTableCellRendererComponent(myTable, TestPhysicalDevices.GOOGLE_PIXEL_3, false, false, 0, 0);

    // Assert
    assertEquals(myTable.getForeground(), renderer.getNameLabel().getForeground());
  }

  private static @NotNull Function<@NotNull Object, @NotNull Border> mockGetBorder(@NotNull Object key, @NotNull Border border) {
    @SuppressWarnings("unchecked")
    Function<Object, Border> getBorder = Mockito.mock(Function.class);

    Mockito.when(getBorder.apply(key)).thenReturn(border);

    return getBorder;
  }
}
