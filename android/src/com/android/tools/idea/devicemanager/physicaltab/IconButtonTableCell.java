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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.tools.idea.devicemanager.IconButton;
import com.android.tools.idea.devicemanager.Tables;
import java.awt.Component;
import java.util.EventObject;
import java.util.function.BiConsumer;
import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.EventListenerList;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import org.jetbrains.annotations.NotNull;

final class IconButtonTableCell implements TableCellRenderer, TableCellEditor {
  private final @NotNull IconButton myButton;
  private final @NotNull Object myValue;

  private final @NotNull EventListenerList myList;

  IconButtonTableCell(@NotNull Icon icon, @NotNull Object value) {
    myButton = new IconButton(icon);
    myValue = value;

    myList = new EventListenerList();
  }

  @Override
  public @NotNull Component getTableCellRendererComponent(@NotNull JTable table,
                                                          @NotNull Object value,
                                                          boolean selected,
                                                          boolean focused,
                                                          int viewRowIndex,
                                                          int viewColumnIndex) {
    return getTableCellComponent(table, selected, focused);
  }

  @Override
  public @NotNull Component getTableCellEditorComponent(@NotNull JTable table,
                                                        @NotNull Object value,
                                                        boolean selected,
                                                        int viewRowIndex,
                                                        int viewColumnIndex) {
    return getTableCellComponent(table, selected, false);
  }

  private @NotNull Component getTableCellComponent(@NotNull JTable table, boolean selected, boolean focused) {
    myButton.setBackground(Tables.getBackground(table, selected));
    myButton.setBorder(Tables.getBorder(selected, focused));
    myButton.setForeground(Tables.getForeground(table, selected));
    myButton.setSelectedInTableCell(selected);

    return myButton;
  }

  @Override
  public @NotNull Object getCellEditorValue() {
    return myValue;
  }

  @Override
  public boolean isCellEditable(@NotNull EventObject event) {
    return true;
  }

  @Override
  public boolean shouldSelectCell(@NotNull EventObject event) {
    return true;
  }

  @Override
  public boolean stopCellEditing() {
    fire(CellEditorListener::editingStopped);
    return true;
  }

  @Override
  public void cancelCellEditing() {
    fire(CellEditorListener::editingCanceled);
  }

  private void fire(@NotNull BiConsumer<@NotNull CellEditorListener, @NotNull ChangeEvent> consumer) {
    Object[] listeners = myList.getListenerList();
    ChangeEvent event = null;

    for (int i = listeners.length - 2; i >= 0; i -= 2) {
      if (!listeners[i].equals(CellEditorListener.class)) {
        continue;
      }

      if (event == null) {
        event = new ChangeEvent(this);
      }

      consumer.accept((CellEditorListener)listeners[i + 1], event);
    }
  }

  @Override
  public void addCellEditorListener(@NotNull CellEditorListener listener) {
    myList.add(CellEditorListener.class, listener);
  }

  @Override
  public void removeCellEditorListener(@NotNull CellEditorListener listener) {
    myList.remove(CellEditorListener.class, listener);
  }
}
