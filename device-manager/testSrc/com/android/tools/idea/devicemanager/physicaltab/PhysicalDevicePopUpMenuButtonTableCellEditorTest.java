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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.devicemanager.DeviceTables;
import com.android.tools.idea.devicemanager.PopUpMenuButtonTableCellEditor;
import com.android.tools.idea.devicemanager.PopUpMenuValue;
import com.android.tools.idea.wearpairing.WearPairingManager;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class PhysicalDevicePopUpMenuButtonTableCellEditorTest {
  private final @NotNull PopUpMenuButtonTableCellEditor myEditor;

  public PhysicalDevicePopUpMenuButtonTableCellEditorTest() {
    myEditor = new PhysicalDevicePopUpMenuButtonTableCellEditor(Mockito.mock(PhysicalDevicePanel.class),
                                                                Mockito.mock(WearPairingManager.class));
  }

  @Test
  public void newPairDeviceItemPhoneAndOnline() {
    // Arrange
    JTable table = DeviceTables.mock(TestPhysicalDevices.ONLINE_GOOGLE_PIXEL_3);
    myEditor.getTableCellEditorComponent(table, PopUpMenuValue.INSTANCE, false, 0, 5);

    // Act
    List<JComponent> items = myEditor.newItems();

    // Assert
    JComponent item = items.get(1);

    assertTrue(item.isEnabled());
    assertEquals("Wear OS virtual device pairing assistant", item.getToolTipText());
  }

  @Test
  public void newPairDeviceItemPhone() {
    // Arrange
    myEditor.getTableCellEditorComponent(DeviceTables.mock(TestPhysicalDevices.GOOGLE_PIXEL_3), PopUpMenuValue.INSTANCE, false, 0, 5);

    // Act
    List<JComponent> items = myEditor.newItems();

    // Assert
    JComponent item = items.get(1);

    assertFalse(item.isEnabled());
    assertEquals("Device must be online to pair with a Wear OS virtual device", item.getToolTipText());
  }
}
