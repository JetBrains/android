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

import com.intellij.ui.scale.JBUIScale;
import java.awt.Component;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IconButtonTableCellRenderer implements TableCellRenderer {
  protected final @NotNull IconButton myButton;

  protected IconButtonTableCellRenderer() {
    this(null);
  }

  public IconButtonTableCellRenderer(@Nullable Icon icon) {
    this(icon, null);
  }

  public IconButtonTableCellRenderer(@Nullable Icon icon, @Nullable String tooltipText) {
    myButton = new IconButton(icon);
    myButton.setToolTipText(tooltipText);
  }

  public static int getPreferredWidth(@NotNull JTable table, @NotNull Class<?> c) {
    return getPreferredWidth(table, c, JBUIScale.scale(8));
  }

  // TODO It looks like the padding accommodates the sorting direction icon. Find a way to programmatically get its width.
  static int getPreferredWidth(@NotNull JTable table, @NotNull Class<? extends @NotNull Object> c, int padding) {
    IconButtonTableCellRenderer renderer = (IconButtonTableCellRenderer)table.getDefaultRenderer(c);
    return renderer.myButton.getTableCellComponent(table, false, false).getPreferredSize().width + padding;
  }

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    return myButton.getTableCellComponent(table, selected, focused);
  }
}
