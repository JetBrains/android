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

import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class ViewDetailsListSelectionListenerTest {
  private final @NotNull DevicePanel myPanel = Mockito.mock(DevicePanel.class);
  private final ListSelectionListener myListener = new ViewDetailsListSelectionListener(myPanel);

  @Test
  public void valueChangedValueIsAdjusting() {
    // Arrange
    ListSelectionEvent event = new ListSelectionEvent(new Object(), 0, 0, true);

    // Act
    myListener.valueChanged(event);

    // Assert
    Mockito.verify(myPanel, Mockito.never()).viewDetails();
  }

  @Test
  public void valueChangedPanelDoesntHaveDetails() {
    // Arrange
    JTable table = Mockito.mock(JTable.class);
    Mockito.when(table.getSelectedRowCount()).thenReturn(1);

    myPanel.myTable = table;
    ListSelectionEvent event = new ListSelectionEvent(new Object(), 0, 0, false);

    // Act
    myListener.valueChanged(event);

    // Assert
    Mockito.verify(myPanel, Mockito.never()).viewDetails();
  }

  @Test
  public void valueChanged() {
    // Arrange
    JTable table = Mockito.mock(JTable.class);
    Mockito.when(table.getSelectedRowCount()).thenReturn(1);

    myPanel.myTable = table;
    Mockito.when(myPanel.hasDetails()).thenReturn(true);

    ListSelectionEvent event = new ListSelectionEvent(new Object(), 0, 0, false);

    // Act
    myListener.valueChanged(event);

    // Assert
    Mockito.verify(myPanel).viewDetails();
  }
}
