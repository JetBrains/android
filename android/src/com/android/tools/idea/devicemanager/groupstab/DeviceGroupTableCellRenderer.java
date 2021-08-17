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
package com.android.tools.idea.devicemanager.groupstab;

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;

final class DeviceGroupTableCellRenderer implements TableCellRenderer {
  private final @NotNull JPanel myPanel;
  private final @NotNull JBLabel myNameLabel;
  private final @NotNull JBLabel myDescriptionLabel;

  DeviceGroupTableCellRenderer() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayout(2, 1));

    // TODO: show devices icon

    myNameLabel = new JBLabel();
    myPanel.add(myNameLabel);

    myDescriptionLabel = new JBLabel();
    myDescriptionLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
    myPanel.add(myDescriptionLabel);

    // TODO: show online status
  }

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    DeviceGroup deviceGroup = (DeviceGroup)value;

    if (table.getSelectedRow() == viewRowIndex) {
      myPanel.setBackground(table.getSelectionBackground());
      myPanel.setForeground(table.getSelectionForeground());
    }
    else {
      myPanel.setBackground(table.getBackground());
      myPanel.setForeground(table.getForeground());
    }

    myNameLabel.setText(deviceGroup.getName() + " (" + deviceGroup.getDevices().size() + ")");

    myDescriptionLabel.setText(deviceGroup.getDescription());

    return myPanel;
  }
}
