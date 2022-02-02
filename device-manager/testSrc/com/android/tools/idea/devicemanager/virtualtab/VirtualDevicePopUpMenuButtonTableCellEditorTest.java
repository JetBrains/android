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

import com.android.tools.idea.devicemanager.PopUpMenuButtonTableCellEditor;
import java.util.Collection;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class VirtualDevicePopUpMenuButtonTableCellEditorTest {
  private final @NotNull Emulator myEmulator = Mockito.mock(Emulator.class);

  private final PopUpMenuButtonTableCellEditor myEditor =
    new VirtualDevicePopUpMenuButtonTableCellEditor(Mockito.mock(VirtualDevicePanel.class), myEmulator);

  @Test
  public void newColdBootNowItemEmulatorDoesntSupportColdBooting() {
    // Act
    Collection<JComponent> items = myEditor.newItems();

    // Assert
    assertEquals(6, items.size());
  }

  @Test
  public void newColdBootNowItem() {
    // Arrange
    Mockito.when(myEmulator.supportsColdBooting()).thenReturn(true);

    // Act
    List<JComponent> items = myEditor.newItems();

    // Assert
    assertEquals(7, items.size());

    AbstractButton item = (AbstractButton)items.get(2);

    assertEquals("Cold Boot Now", item.getText());
    assertEquals("Force one cold boot", item.getToolTipText());
  }
}
