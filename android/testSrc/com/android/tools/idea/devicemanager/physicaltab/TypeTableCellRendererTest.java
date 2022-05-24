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
import static org.junit.Assert.assertNull;

import com.intellij.ui.table.JBTable;
import icons.StudioIcons;
import java.util.Collections;
import javax.swing.JLabel;
import javax.swing.JTable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class TypeTableCellRendererTest {
  private final TypeTableCellRenderer myRenderer = new TypeTableCellRenderer();
  private final JTable myTable = new JBTable();

  @Test
  public void getTableCellRendererComponentValueEqualsUsbAndWiFiSet() {
    // Act
    Object component = myRenderer.getTableCellRendererComponent(myTable, ConnectionType.USB_AND_WI_FI_SET, false, false, 0, 2);

    // Assert
    assertEquals(myRenderer.getPanel(), component);
  }

  @Test
  public void getTableCellRendererComponentValueEqualsEmptySet() {
    // Act
    Object component = myRenderer.getTableCellRendererComponent(myTable, Collections.EMPTY_SET, false, false, 0, 2);

    // Assert
    JLabel label = myRenderer.getLabel();

    assertNull(label.getIcon());
    assertEquals(label, component);
  }

  @Test
  public void getTableCellRendererComponentValueEqualsUsbSet() {
    // Act
    Object component = myRenderer.getTableCellRendererComponent(myTable, ConnectionType.USB_SET, false, false, 0, 2);

    // Assert
    JLabel label = myRenderer.getLabel();

    assertEquals(StudioIcons.Avd.CONNECTION_USB, label.getIcon());
    assertEquals(label, component);
  }

  @Test
  public void getTableCellRendererComponentValueEqualsWiFiSet() {
    // Act
    Object component = myRenderer.getTableCellRendererComponent(myTable, ConnectionType.WI_FI_SET, false, false, 0, 2);

    // Assert
    JLabel label = myRenderer.getLabel();

    assertEquals(StudioIcons.Avd.CONNECTION_WIFI, label.getIcon());
    assertEquals(label, component);
  }
}
