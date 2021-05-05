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
package com.android.tools.idea.devicemanager.groupstab.create;

import com.android.tools.idea.devicemanager.groupstab.GroupableDevice;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.Icon;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;

final class GroupableDeviceTableCellRenderer implements TableCellRenderer {
  private final @NotNull JPanel myPanel;
  private final @NotNull JBLabel myIconLabel;
  private final @NotNull JBLabel myNameLabel;
  private final @NotNull JBLabel myStatusLabel;
  private final @NotNull JBLabel myDescriptionLabel;

  GroupableDeviceTableCellRenderer() {
    myPanel = new JPanel(new GridBagLayout());

    myIconLabel = new JBLabel();
    GridBagConstraints iconConstraints = new GridBagConstraints();
    iconConstraints.gridx = 0;
    iconConstraints.gridy = 0;
    iconConstraints.gridheight = 2;
    iconConstraints.anchor = GridBagConstraints.WEST;
    iconConstraints.insets = JBUI.insets(10, 10, 10, 0);
    myPanel.add(myIconLabel, iconConstraints);

    myNameLabel = new JBLabel();
    GridBagConstraints nameConstraints = new GridBagConstraints();
    nameConstraints.gridx = 1;
    nameConstraints.gridy = 0;
    nameConstraints.anchor = GridBagConstraints.SOUTHWEST;
    nameConstraints.insets = JBUI.insetsTop(10);
    myPanel.add(myNameLabel, nameConstraints);

    myStatusLabel = new JBLabel();
    GridBagConstraints statusConstraints = new GridBagConstraints();
    statusConstraints.weightx = 1;
    statusConstraints.gridx = 2;
    statusConstraints.gridy = 0;
    statusConstraints.anchor = GridBagConstraints.SOUTHWEST;
    statusConstraints.insets = JBUI.insetsLeft(5);
    myPanel.add(myStatusLabel, statusConstraints);

    myDescriptionLabel = new JBLabel();
    myDescriptionLabel.setFontColor(UIUtil.FontColor.BRIGHTER);
    GridBagConstraints descriptionConstraints = new GridBagConstraints();
    descriptionConstraints.weightx = 1;
    descriptionConstraints.gridx = 1;
    descriptionConstraints.gridy = 1;
    descriptionConstraints.gridwidth = 2;
    descriptionConstraints.anchor = GridBagConstraints.NORTHWEST;
    descriptionConstraints.insets = JBUI.insetsBottom(10);
    myPanel.add(myDescriptionLabel, descriptionConstraints);
  }

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    GroupableDevice groupableDevice = (GroupableDevice)value;

    if (table.getSelectedRow() == viewRowIndex) {
      myPanel.setBackground(table.getSelectionBackground());
      myPanel.setForeground(table.getSelectionForeground());
    }
    else {
      myPanel.setBackground(table.getBackground());
      myPanel.setForeground(table.getForeground());
    }

    Icon icon = table.getSelectedRow() == viewRowIndex ? groupableDevice.getHighlightedIcon() : groupableDevice.getIcon();
    myIconLabel.setIcon(icon);

    myNameLabel.setText(groupableDevice.getName());

    myStatusLabel.setIcon(groupableDevice.isOnline() ? StudioIcons.Common.CIRCLE_GREEN : StudioIcons.Common.CIRCLE_RED);

    myDescriptionLabel.setText("TODO"); // TODO: "Android 10.0+ Google API | x86"

    return myPanel;
  }
}
