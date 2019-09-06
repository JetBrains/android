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
package com.android.tools.idea.npw.assetstudio.ui;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.openapi.ui.DialogWrapper;
import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

public class IconPickerDialogTest {
  @Test
  public void categoryNames() {
    assertThat(IconPickerDialog.getCategoryNames()).isEqualTo(new String[]{
      "All",
      "Action",
      "Alert",
      "Audio/Video",
      "Communication",
      "Content",
      "Device",
      "Editor",
      "File",
      "Hardware",
      "Image",
      "Maps",
      "Navigation",
      "Notification",
      "Places",
      "Social",
      "Toggle"});
  }

  @Test
  public void testFiltering() throws InvocationTargetException, InterruptedException {
    SwingUtilities.invokeAndWait(() -> {
      IconPickerDialog dialog = new IconPickerDialog(null);
      JComponent panel = dialog.createCenterPanel();
      panel.setVisible(true);

      dialog.setFilter("ala");

      assertThat(tableToString(dialog.getTable())).isEqualTo(
        "access alarm        access alarms       account balance     account balance w...add alarm           alarm add           \n" +
        "alarm               alarm off           alarm on                                                                        \n"
      );

      dialog.setFilter("alar");

      assertThat(tableToString(dialog.getTable())).isEqualTo(
        "access alarm        access alarms       add alarm           alarm add           alarm               alarm off           \n" +
        "alarm on                                                                                                                \n"
      );
      dialog.close(DialogWrapper.CLOSE_EXIT_CODE);
    });
  }

  @NotNull
  public static String tableToString(@NotNull JTable table) {
    return tableToString(table, 0, Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 20);
  }

  @NotNull
  public static String tableToString(@NotNull JTable table, int startRow, int endRow, int startColumn, int endColumn,
                                     int cellWidth) {
    StringBuilder sb = new StringBuilder();

    String formatString = "%-" + Integer.toString(cellWidth) + "s";
    for (int row = Math.max(0, startRow); row < Math.min(endRow, table.getRowCount()); row++) {
      for (int column = Math.max(0, startColumn); column < Math.min(table.getColumnCount(), endColumn); column++) {
        Object value = table.getValueAt(row, column);
        TableCellRenderer renderer = table.getCellRenderer(row, column);
        Component component = renderer.getTableCellRendererComponent(table, value, false, false, row, column);

        JLabel label = (JLabel)component;
        assertThat(label.getText()).isEmpty();
        String cell = label.getAccessibleContext().getAccessibleName();
        if (cell.length() > cellWidth) {
          cell = cell.substring(0, cellWidth - 3) + "...";
        }
        sb.append(String.format(formatString, cell));
      }
      sb.append('\n');
    }

    return sb.toString();
  }
}