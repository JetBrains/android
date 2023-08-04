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

import com.android.tools.adtui.table.Tables;
import com.android.tools.idea.devicemanager.ConnectionType;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.components.JBPanel;
import icons.StudioIcons;
import java.awt.Component;
import java.util.Collections;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Group;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;

final class TypeTableCellRenderer implements TableCellRenderer {
  private final UsbAndWiFiPanel myPanel = new UsbAndWiFiPanel();
  private final IconLabel myLabel = new IconLabel();

  private static final class UsbAndWiFiPanel extends JBPanel<UsbAndWiFiPanel> {
    private UsbAndWiFiPanel() {
      super(null);

      Component usbLabel = new IconLabel(StudioIcons.Avd.CONNECTION_USB);
      Component wiFiLabel = new IconLabel(StudioIcons.Avd.CONNECTION_WIFI);

      GroupLayout layout = new GroupLayout(this);

      Group horizontalGroup = layout.createSequentialGroup()
        .addComponent(usbLabel)
        .addComponent(wiFiLabel);

      Group verticalGroup = layout.createSequentialGroup()
        .addContainerGap(0, Short.MAX_VALUE)
        .addGroup(layout.createParallelGroup()
                    .addComponent(usbLabel)
                    .addComponent(wiFiLabel))
        .addContainerGap(0, Short.MAX_VALUE);

      layout.setAutoCreateGaps(true);
      layout.setHorizontalGroup(horizontalGroup);
      layout.setVerticalGroup(verticalGroup);

      setLayout(layout);
    }

    private @NotNull Component getTableCellComponent(@NotNull JTable table, boolean selected, boolean focused) {
      setBackground(Tables.getBackground(table, selected));
      setBorder(Tables.getBorder(selected, focused));

      return this;
    }
  }

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    if (value.equals(ConnectionType.USB_AND_WI_FI_SET)) {
      return myPanel.getTableCellComponent(table, selected, focused);
    }

    if (value.equals(Collections.EMPTY_SET)) {
      myLabel.setDefaultIcon(null);
    }
    else if (value.equals(ConnectionType.UNKNOWN_SET)) {
      myLabel.setDefaultIcon(StudioIcons.Avd.CONNECTION_GENERIC);
    }
    else if (value.equals(ConnectionType.USB_SET)) {
      myLabel.setDefaultIcon(StudioIcons.Avd.CONNECTION_USB);
    }
    else if (value.equals(ConnectionType.WI_FI_SET)) {
      myLabel.setDefaultIcon(StudioIcons.Avd.CONNECTION_WIFI);
    }
    else {
      assert false : value;
    }

    return myLabel.getTableCellComponent(table, selected, focused);
  }

  @VisibleForTesting
  @NotNull Object getPanel() {
    return myPanel;
  }

  @VisibleForTesting
  @NotNull JLabel getLabel() {
    return myLabel;
  }
}
