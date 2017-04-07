/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.strings.table;

import com.android.tools.idea.rendering.Locale;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

final class LocaleRenderer implements TableCellRenderer {
  private final TableCellRenderer myRenderer;
  private final StringResourceTableModel myModel;

  LocaleRenderer(@NotNull TableCellRenderer renderer, @NotNull StringResourceTableModel model) {
    myRenderer = renderer;
    myModel = model;
  }

  @NotNull
  @Override
  public Component getTableCellRendererComponent(@NotNull JTable table,
                                                 @NotNull Object value,
                                                 boolean selected,
                                                 boolean focused,
                                                 int row,
                                                 int column) {
    Component component = myRenderer.getTableCellRendererComponent(table, value, selected, focused, row, column);

    if (component instanceof JLabel) {
      Locale locale = myModel.getLocale(column);
      assert locale != null;

      ((JLabel)component).setIcon(locale.getFlagImage());
    }

    return component;
  }
}
