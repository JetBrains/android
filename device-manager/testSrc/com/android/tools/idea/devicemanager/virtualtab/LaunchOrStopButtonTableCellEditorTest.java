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
package com.android.tools.idea.devicemanager.virtualtab;

import static org.junit.Assert.assertEquals;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.tools.idea.devicemanager.DeviceTables;
import com.android.tools.idea.devicemanager.IconButton;
import icons.StudioIcons;
import java.util.Optional;
import javax.swing.JTable;
import javax.swing.event.CellEditorListener;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class LaunchOrStopButtonTableCellEditorTest {
  private final @NotNull AvdInfo myAvd;
  private final @NotNull CellEditorListener myListener;
  private final @NotNull LaunchOrStopButtonTableCellEditor myEditor;

  public LaunchOrStopButtonTableCellEditorTest() {
    myAvd = Mockito.mock(AvdInfo.class);
    myListener = Mockito.mock(CellEditorListener.class);

    myEditor = new LaunchOrStopButtonTableCellEditor();
    myEditor.addCellEditorListener(myListener);
  }

  @Test
  public void getTableCellEditorComponentDeviceIsOnline() {
    // Arrange
    VirtualDevice device = TestVirtualDevices.onlinePixel5Api31(myAvd);
    JTable table = DeviceTables.mock(device);

    // Act
    IconButton component = (IconButton)myEditor.getTableCellEditorComponent(table, VirtualDevice.State.LAUNCHED, false, 0, 3);
    component.doClick();

    // Assert
    assertEquals(device, myEditor.getDevice());

    assertEquals(Optional.of(StudioIcons.Avd.STOP), component.getDefaultIcon());
    assertEquals("Stop the emulator running this AVD", component.getToolTipText());

    assertEquals(VirtualDevice.State.STOPPING, myEditor.getCellEditorValue());
    Mockito.verify(myListener).editingStopped(myEditor.getChangeEvent());
  }

  @Test
  public void getTableCellEditorComponent() {
    // Arrange
    Mockito.when(myAvd.getStatus()).thenReturn(AvdStatus.OK);

    VirtualDevice device = TestVirtualDevices.pixel5Api31(myAvd);
    JTable table = DeviceTables.mock(device);

    // Act
    IconButton component = (IconButton)myEditor.getTableCellEditorComponent(table, VirtualDevice.State.STOPPED, false, 0, 3);
    component.doClick();

    // Assert
    assertEquals(device, myEditor.getDevice());

    assertEquals(Optional.of(StudioIcons.Avd.RUN), component.getDefaultIcon());
    assertEquals("Launch this AVD in the emulator", component.getToolTipText());

    assertEquals(VirtualDevice.State.LAUNCHING, myEditor.getCellEditorValue());
    Mockito.verify(myListener).editingStopped(myEditor.getChangeEvent());
  }
}
