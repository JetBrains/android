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

import com.android.tools.idea.rendering.FlagManager;
import com.android.tools.idea.rendering.Locale;
import org.jetbrains.annotations.NotNull;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import java.awt.Component;

final class LocaleRenderer implements TableCellRenderer {
  private final TableCellRenderer myRenderer;

  LocaleRenderer(@NotNull TableCellRenderer renderer) {
    myRenderer = renderer;
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
    TableModel model = table.getModel();

    if (component instanceof JLabel && model instanceof StringResourceTableModel) {
      Locale locale = ((StringResourceTableModel)model).getLocale(table.convertColumnIndexToModel(column));
      assert locale != null;
      if (FlagManager.showFlagsForLanguages()) {
        ((JLabel)component).setIcon(locale.getFlagImage());
      } else {
        ((JLabel)component).setIcon(null);
      }
    }

    return component;
  }
}
