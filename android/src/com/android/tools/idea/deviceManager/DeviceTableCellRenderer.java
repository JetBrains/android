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
package com.android.tools.idea.deviceManager;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.UIUtil.FontSize;
import icons.StudioIcons;
import java.awt.Color;
import java.awt.Component;
import java.util.function.Function;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;

public final class DeviceTableCellRenderer<D extends Device> implements TableCellRenderer {
  private final @NotNull JLabel myIconLabel;
  private final @NotNull JLabel myNameLabel;
  private final @NotNull JLabel myConnectedLabel;
  private final @NotNull JLabel myLine2Label;
  private final @NotNull JComponent myPanel;

  private final @NotNull Class<@NotNull D> myValueClass;
  private final @NotNull Function<@NotNull Object, @NotNull Border> myGetBorder;

  public DeviceTableCellRenderer(@NotNull Class<@NotNull D> valueClass) {
    this(valueClass, UIManager::getBorder);
  }

  @VisibleForTesting
  DeviceTableCellRenderer(@NotNull Class<@NotNull D> valueClass, @NotNull Function<@NotNull Object, @NotNull Border> getBorder) {
    myIconLabel = new JBLabel();
    myNameLabel = new JBLabel();
    myConnectedLabel = new JBLabel();
    myLine2Label = new JBLabel();

    myPanel = new JBPanel<>(null);
    GroupLayout layout = new GroupLayout(myPanel);

    Group horizontalGroup = layout.createSequentialGroup()
      .addComponent(myIconLabel)
      .addPreferredGap(ComponentPlacement.RELATED)
      .addGroup(layout.createParallelGroup()
                  .addGroup(layout.createSequentialGroup()
                              .addComponent(myNameLabel)
                              .addPreferredGap(ComponentPlacement.RELATED)
                              .addComponent(myConnectedLabel))
                  .addComponent(myLine2Label));

    Group verticalGroup = layout.createSequentialGroup()
      .addGroup(layout.createParallelGroup(Alignment.CENTER)
                  .addComponent(myIconLabel)
                  .addComponent(myNameLabel)
                  .addComponent(myConnectedLabel))
      .addComponent(myLine2Label);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    myPanel.setLayout(layout);

    myValueClass = valueClass;
    myGetBorder = getBorder;
  }

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    D device = myValueClass.cast(value);
    Color foreground = getForeground(table, selected);

    myIconLabel.setIcon(device.getIcon());

    myNameLabel.setForeground(foreground);
    myNameLabel.setText(device.getName());

    myConnectedLabel.setIcon(device.isConnected() ? StudioIcons.Common.CIRCLE_GREEN : StudioIcons.Common.CIRCLE_RED);

    myLine2Label.setFont(UIUtil.getLabelFont(FontSize.SMALL));
    myLine2Label.setForeground(brighten(foreground));
    myLine2Label.setText(getLine2(device));

    myPanel.setBackground(getBackground(table, selected));
    myPanel.setBorder(getBorder(selected, focused));

    return myPanel;
  }

  @NotNull String getLine2(@NotNull D device) {
    return device.getTarget();
  }

  private static @NotNull Color getBackground(@NotNull JTable table, boolean selected) {
    if (selected) {
      return table.getSelectionBackground();
    }

    return table.getBackground();
  }

  private @NotNull Border getBorder(boolean selected, boolean focused) {
    if (!focused) {
      return myGetBorder.apply("Table.cellNoFocusBorder");
    }

    if (selected) {
      return myGetBorder.apply("Table.focusSelectedCellHighlightBorder");
    }

    return myGetBorder.apply("Table.focusCellHighlightBorder");
  }

  private static @NotNull Color getForeground(@NotNull JTable table, boolean selected) {
    if (selected) {
      return table.getSelectionForeground();
    }

    return table.getForeground();
  }

  private static @NotNull Color brighten(@NotNull Color color) {
    int red = Math.min(color.getRed() + 50, 255);
    int green = Math.min(color.getGreen() + 50, 255);
    int blue = Math.min(color.getBlue() + 50, 255);

    return new JBColor(new Color(red, green, blue), color.darker());
  }

  @VisibleForTesting
  @NotNull JLabel getIconLabel() {
    return myIconLabel;
  }

  @VisibleForTesting
  @NotNull JLabel getNameLabel() {
    return myNameLabel;
  }

  @VisibleForTesting
  @NotNull JLabel getConnectedLabel() {
    return myConnectedLabel;
  }

  @VisibleForTesting
  @NotNull JLabel getLine2Label() {
    return myLine2Label;
  }

  @VisibleForTesting
  @NotNull JComponent getPanel() {
    return myPanel;
  }
}
