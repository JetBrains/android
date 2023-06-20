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

import com.android.tools.idea.wearpairing.WearPairingManager;
import com.intellij.openapi.project.Project;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDeviceTableTest {
  private final @NotNull PhysicalDeviceTable myTable;

  public PhysicalDeviceTableTest() {
    PhysicalDevicePanel panel = Mockito.mock(PhysicalDevicePanel.class);
    Mockito.when(panel.getProject()).thenReturn(Mockito.mock(Project.class));

    myTable = new PhysicalDeviceTable(panel,
                                      Mockito.mock(WearPairingManager.class),
                                      new PhysicalDeviceTableModel(List.of(TestPhysicalDevices.GOOGLE_PIXEL_3)));
  }

  @Test
  public void getSelectedDeviceViewRowIndexEqualsNegativeOne() {
    // Act
    Object device = myTable.getSelectedDevice();

    // Assert
    assertEquals(Optional.empty(), device);
  }

  @Test
  public void getSelectedDevice() {
    // Arrange
    myTable.setRowSelectionInterval(0, 0);

    // Act
    Object device = myTable.getSelectedDevice();

    // Assert
    assertEquals(Optional.of(TestPhysicalDevices.GOOGLE_PIXEL_3), device);
  }
}
