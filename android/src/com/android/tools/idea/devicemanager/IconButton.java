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

import com.android.tools.adtui.common.ColoredIconGenerator;
import com.intellij.util.ui.JBDimension;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JTable;
import org.jetbrains.annotations.NotNull;

public final class IconButton extends JButton {
  private final @NotNull Icon myIcon;

  public IconButton(@NotNull Icon icon) {
    super(icon);
    Dimension size = new JBDimension(22, 22);

    setBorder(null);
    setContentAreaFilled(false);
    setMaximumSize(size);
    setMinimumSize(size);
    setPreferredSize(size);

    myIcon = icon;
  }

  public @NotNull Component getTableCellComponent(@NotNull JTable table, boolean selected, boolean focused) {
    setBackground(Tables.getBackground(table, selected));
    setBorder(Tables.getBorder(selected, focused));
    setForeground(Tables.getForeground(table, selected));
    setSelectedInTableCell(selected);

    return this;
  }

  public void setSelectedInTableCell(boolean selected) {
    setIcon(selected ? ColoredIconGenerator.INSTANCE.generateColoredIcon(myIcon, getForeground()) : myIcon);
  }
}
