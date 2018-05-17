/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.tools.idea.devicemanager.DeviceTableCellRenderer;
import com.intellij.ui.table.JBTable;
import icons.StudioIcons;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDeviceTableCellRendererTest {
  private final @NotNull AvdInfo myAvd = Mockito.mock(AvdInfo.class);
  private final @NotNull DeviceTableCellRenderer<VirtualDevice> myRenderer = new VirtualDeviceTableCellRenderer();
  private final @NotNull JTable myTable = new JBTable();
  private final @NotNull Object myDevice = TestVirtualDevices.onlinePixel5Api31(myAvd);

  @Test
  public void getTableCellRendererComponentStatusDoesntEqualOk() {
    // Arrange
    Mockito.when(myAvd.getStatus()).thenReturn(AvdStatus.ERROR_IMAGE_MISSING);
    Mockito.when(myAvd.getErrorMessage()).thenReturn("Missing system image for x86_64 Pixel 5.");

    // Act
    myRenderer.getTableCellRendererComponent(myTable, myDevice, false, false, 0, 0);

    // Assert
    assertEquals(StudioIcons.Common.WARNING_INLINE, myRenderer.getStateLabel().getIcon());
    assertEquals("Missing system image for x86_64 Pixel 5.", myRenderer.getLine2Label().getText());
  }

  @Test
  public void getTableCellRendererComponent() {
    // Arrange
    Mockito.when(myAvd.getStatus()).thenReturn(AvdStatus.OK);

    // Act
    myRenderer.getTableCellRendererComponent(myTable, myDevice, false, false, 0, 0);

    // Assert
    assertEquals(StudioIcons.Avd.STATUS_DECORATOR_ONLINE, myRenderer.getStateLabel().getIcon());
    assertEquals("Android 12.0 Google APIs | x86_64", myRenderer.getLine2Label().getText());
  }
}
