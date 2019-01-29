/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.swing.laf;

import com.android.tools.adtui.swing.FakeKeyboard;
import com.android.tools.adtui.swing.FakeMouse;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicListUI;
import javax.swing.plaf.basic.BasicTableUI;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * A stubbed {@link BasicTableUI} for use in headless unit tests, where some functionality is
 * removed to avoid making calls that would otherwise throw a {@link HeadlessException}. This will
 * allow you to interact with {@link JTable} components using {@link FakeMouse} and
 * {@link FakeKeyboard}.
 *
 * To use, you must remember to call {@code someTable.setUI(new HeadlessTableUI()} before calling
 * methods like {@link FakeMouse#click(int, int)} or {@link FakeKeyboard#press(FakeKeyboard.Key)}.
 *
 * NOTE: Changing the UI of a component can subtly change its behavior! This class may need to be
 * updated in the future to add more functionality, so it more closely matches its parent class.
 */
public class HeadlessTableUI extends BasicTableUI {
  private final MouseListener myMouseListener = new HeadlessTableUI.HeadlessMouseListener();

  @Override
  protected void installListeners() {
    table.addMouseListener(myMouseListener);
  }

  @Override
  protected void uninstallListeners() {
    table.removeMouseListener(myMouseListener);
  }

  /**
   * A minimal mouse listener, which only does a subset of what {@link BasicTableUI}'s mouse handler
   * does. This allows it to avoid calling
   * {@link BasicGraphicsUtils#isMenuShortcutKeyDown(InputEvent)}, which fails in headless mode.
   */
  private final class HeadlessMouseListener extends MouseAdapter {
    private int pressedRow;
    private int pressedCol;
    private Component dispatchComponent;

    @Override
    public void mousePressed(MouseEvent e) {
      if (SwingUtilities2.shouldIgnore(e, table)) {
        return;
      }

      if (table.isEditing() && !table.getCellEditor().stopCellEditing()) {
        Component editorComponent = table.getEditorComponent();
        if (editorComponent != null && !editorComponent.hasFocus()) {
          SwingUtilities2.compositeRequestFocus(editorComponent);
        }
        return;
      }

      Point p = e.getPoint();
      pressedRow = table.rowAtPoint(p);
      pressedCol = table.columnAtPoint(p);

      SwingUtilities2.adjustFocus(table);
      setValueIsAdjusting(true);
      adjustSelection(e);
    }

    private void setValueIsAdjusting(boolean flag) {
      table.getSelectionModel().setValueIsAdjusting(flag);
      table.getColumnModel().getSelectionModel().
        setValueIsAdjusting(flag);
    }

    private void adjustSelection(MouseEvent e) {
      // The autoscroller can generate drag events outside the
      // table's range.
      if ((pressedCol == -1) || (pressedRow == -1)) {
        return;
      }

      boolean dragEnabled = table.getDragEnabled();

      if (!dragEnabled && table.editCellAt(pressedRow, pressedCol, e)) {
        setDispatchComponent(e);
        repostEvent(e);
      }

      CellEditor editor = table.getCellEditor();
      if (dragEnabled || editor == null || editor.shouldSelectCell(e)) {
        table.changeSelection(pressedRow, pressedCol,
                              false,
                              e.isShiftDown());
      }
    }

    private void setDispatchComponent(MouseEvent e) {
      Component editorComponent = table.getEditorComponent();
      Point p = e.getPoint();
      Point p2 = SwingUtilities.convertPoint(table, p, editorComponent);
      dispatchComponent =
        SwingUtilities.getDeepestComponentAt(editorComponent,
                                             p2.x, p2.y);
      SwingUtilities2.setSkipClickCount(dispatchComponent,
                                        e.getClickCount() - 1);
    }

    private boolean repostEvent(MouseEvent e) {
      // Check for isEditing() in case another event has
      // caused the editor to be removed. See bug #4306499.
      if (dispatchComponent == null || !table.isEditing()) {
        return false;
      }
      MouseEvent e2 = SwingUtilities.convertMouseEvent(table, e,
                                                       dispatchComponent);
      dispatchComponent.dispatchEvent(e2);
      return true;
    }
  }
}
