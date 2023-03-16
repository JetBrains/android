/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.sdklib.AndroidVersion;
import com.intellij.ui.components.JBLabel;
import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;

public final class ApiTableCellRenderer implements TableCellRenderer {
  private final JLabel myLabel = new JBLabel();

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    myLabel.setBackground(Tables.getBackground(table, selected));
    myLabel.setBorder(Tables.getBorder(selected, focused));
    myLabel.setForeground(Tables.getForeground(table, selected));
    myLabel.setText(((AndroidVersion)value).getApiStringWithExtension());

    return myLabel;
  }
}
