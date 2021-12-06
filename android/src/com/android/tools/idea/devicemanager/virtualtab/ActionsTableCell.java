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
package com.android.tools.idea.devicemanager.virtualtab;

import com.android.tools.idea.avdmanager.AvdActionPanel;
import com.android.tools.idea.avdmanager.AvdActionPanel.AvdRefreshProvider;
import com.android.tools.idea.devicemanager.Tables;
import com.android.tools.idea.devicemanager.virtualtab.VirtualDeviceTableModel.Actions;
import com.intellij.util.ui.AbstractTableCellEditor;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class ActionsTableCell extends AbstractTableCellEditor implements TableCellRenderer {
  private final @NotNull AvdRefreshProvider myRefreshProvider;
  private final @NotNull Map<@NotNull VirtualDevice, @NotNull AvdActionPanel> myDeviceToComponentMap;

  ActionsTableCell(@NotNull AvdRefreshProvider refreshProvider) {
    myRefreshProvider = refreshProvider;
    myDeviceToComponentMap = new HashMap<>();
  }

  @NotNull AvdActionPanel getComponent(@NotNull VirtualDevice device) {
    return myDeviceToComponentMap.get(device);
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @Nullable Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    return getTableCellComponent((VirtualDeviceTable)table, selected, viewRowIndex);
  }

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @Nullable Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    AvdActionPanel component = getTableCellComponent((VirtualDeviceTable)table, selected, viewRowIndex);
    component.setFocused(false);

    return component;
  }

  private @NotNull AvdActionPanel getTableCellComponent(@NotNull VirtualDeviceTable table, boolean selected, int viewRowIndex) {
    AvdActionPanel component = myDeviceToComponentMap.computeIfAbsent(table.getDeviceAt(viewRowIndex), this::newAvdActionPanel);

    component.setBackground(Tables.getBackground(table, selected));
    component.setForeground(Tables.getForeground(table, selected));
    component.setHighlighted(selected);

    return component;
  }

  private @NotNull AvdActionPanel newAvdActionPanel(@NotNull VirtualDevice device) {
    boolean projectNull = myRefreshProvider.getProject() == null;
    return new AvdActionPanel(myRefreshProvider, device.getAvdInfo(), true, !projectNull, projectNull ? 2 : 3);
  }

  @Override
  public @NotNull Object getCellEditorValue() {
    return Actions.INSTANCE;
  }
}
