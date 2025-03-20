/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.adtui.swing.FakeKeyboardFocusManager;
import com.android.tools.adtui.swing.FakeUi;
import com.android.tools.adtui.swing.laf.HeadlessTableUI;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.DisposableRule;
import com.intellij.testFramework.RuleChain;
import com.intellij.util.ArrayUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import javax.swing.DefaultRowSorter;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.Rule;
import org.junit.Test;

public final class FrozenColumnTableTest {
  private final DisposableRule myRule = new DisposableRule();

  @Rule
  public RuleChain chain = new RuleChain(new ApplicationRule(), myRule);

  @Test
  public void getRowHeight() {
    FrozenColumnTable<DefaultTableModel> frozenColumnTable = new FrozenColumnTable<>(new DefaultTableModel(1, 4), 2);

    JTable frozenTable = frozenColumnTable.getFrozenTable();
    frozenTable.setRowHeight(26);
    assertThat(frozenTable.getRowHeight()).isEqualTo(26);

    JTable scrollableTable = frozenColumnTable.getScrollableTable();
    scrollableTable.setRowHeight(29);
    assertThat(scrollableTable.getRowHeight()).isEqualTo(29);

    assertThat(frozenColumnTable.getRowHeight()).isEqualTo(29);
  }

  @Test
  public void tableIsScrollableEvenWithoutLocales() {
    Object[][] data = new Object[][] {
      new Object[]{"east", "app/src/main/res", false, "east"},
      new Object[]{"east", "app/src/main/res", false, "east"},
      new Object[]{"north", "app/src/main/res", false, "north"}
    };
    Object[] columns = new Object[]{"Key", "Resource Folder", "Untranslatable", "Default Value"};
    DefaultTableModel model = new DefaultTableModel(data, columns);
    FrozenColumnTable<DefaultTableModel> frozenColumnTable = new FrozenColumnTable<>(model, 4);
    frozenColumnTable.getFrozenTable().createDefaultColumnsFromModel();
    frozenColumnTable.getScrollableTable().createDefaultColumnsFromModel();
    JScrollPane pane = (JScrollPane)frozenColumnTable.getScrollPane();
    pane.setBounds(0, 0, 800, 20);
    new FakeUi(pane, 1.0, true);
    pane.doLayout();
    assertThat(pane.getVerticalScrollBar().isVisible()).isTrue();
  }

  @Test
  public void tableKeysCanJumpTable() {
    Object[][] data = new Object[][] {
      new Object[]{"east", "app/src/main/res", false, "east", "est", "øst"},
      new Object[]{"west", "app/src/main/res", false, "west", "ouest", "vest"},
      new Object[]{"north", "app/src/main/res", false, "north", "nord", "nord"},
      new Object[]{"south", "app/src/main/res", false, "south", "sud", "syd"}
    };
    Object[] columns = new Object[]{"Key", "Resource Folder", "Untranslatable", "Default Value", "French (fr)", "Danish (da)"};
    DefaultTableModel model = new DefaultTableModel(data, columns);
    FrozenColumnTable<DefaultTableModel> frozenColumnTable = new FrozenColumnTable<>(model, 4);
    JTable frozenTable = frozenColumnTable.getFrozenTable();
    JTable scrollableTable = frozenColumnTable.getScrollableTable();
    frozenTable.createDefaultColumnsFromModel();
    scrollableTable.createDefaultColumnsFromModel();
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(frozenTable, BorderLayout.CENTER);
    panel.add(frozenColumnTable.getScrollPane(), BorderLayout.WEST);
    FakeUi ui = new FakeUi(panel, 1.0, true, myRule.getDisposable());
    FakeKeyboardFocusManager focusManager = new FakeKeyboardFocusManager(myRule.getDisposable());
    focusManager.setFocusOwner(frozenColumnTable.getFrozenTable());
    moveTo(frozenColumnTable, 1, 3);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{3});

    // Move right jumps to scrollable table:
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{4});

    // Move left jumps to frozen table:
    ui.keyboard.pressAndRelease(KeyEvent.VK_LEFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{3});

    // Move to end jumps to scrollable table:
    ui.keyboard.pressAndRelease(KeyEvent.VK_END);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{5});

    // Move to the right is a noop:
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{5});

    // Move to home jumps to frozen table:
    ui.keyboard.pressAndRelease(KeyEvent.VK_HOME);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{0});

    // Select a range of rows:
    moveTo(frozenColumnTable, 1, 3);
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN);
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1, 2, 3});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{3});

    // Move right extending the selection jumps to scrollable table:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1, 2, 3});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{3, 4});

    // Move left extending the selection jumps to frozen table:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_LEFT);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1, 2, 3});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{3});

    // Move to end extending the selection jumps to scrollable table:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_END);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1, 2, 3});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{3, 4, 5});

    // Move to home jumps to frozen table:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_HOME);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1, 2, 3});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{3, 2, 1, 0});

    // Select a new range of rows with the lead on the top:
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_UP);
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_UP);
    ui.keyboard.pressAndRelease(KeyEvent.VK_UP);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{2, 1, 0});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{3});

    // Move right extending the selection jumps to scrollable table:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{2, 1, 0});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{3, 4});

    // Move left extending the selection jumps to frozen table:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_LEFT);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{2, 1, 0});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{3});

    // Move to end extending the selection jumps to scrollable table:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_END);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{2, 1, 0});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{3, 4, 5});

    // Move to home jumps to frozen table:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_HOME);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{2, 1, 0});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{3, 2, 1, 0});

    // Move to the right should select a single cell:
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{0});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{1});

    // Select columns from both tables:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{0});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{1, 2, 3, 4});

    // Move down removes column selection:
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{4});

    // Select columns from both tables:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_LEFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_LEFT);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{4, 3, 2});

    // Move down removes column selection:
    ui.keyboard.pressAndRelease(KeyEvent.VK_UP);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{0});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{2});

    // Select columns from both tables:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{0});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{2, 3, 4, 5});

    // Move down to last row removes column selection:
    ui.keyboard.press(KeyEvent.VK_CONTROL);
    ui.keyboard.pressAndRelease(KeyEvent.VK_END);
    ui.keyboard.release(KeyEvent.VK_CONTROL);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{3});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{5});

    // Select columns from both tables:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_LEFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_LEFT);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{3});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{5, 4, 3});

    // Move up to first row removes column selection:
    ui.keyboard.press(KeyEvent.VK_CONTROL);
    ui.keyboard.pressAndRelease(KeyEvent.VK_HOME);
    ui.keyboard.release(KeyEvent.VK_CONTROL);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{0});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{3});


  }

  @Test
  public void rowSorting() {
    FrozenColumnTable<DefaultTableModel> frozenColumnTable = new FrozenColumnTable<>(new DefaultTableModel(1, 4), 2);
    FrozenColumnTableRowSorter<DefaultTableModel> rowSorter =
      new FrozenColumnTableRowSorter<>(new DefaultRowSorter<>() {}, frozenColumnTable);

    frozenColumnTable.setRowSorter(rowSorter);

    assertThat(frozenColumnTable.getRowSorter()).isEqualTo(rowSorter);
    assertThat(frozenColumnTable.getFrozenTable().getRowSorter()).isEqualTo(rowSorter.getFrozenTableRowSorter());
    assertThat(frozenColumnTable.getScrollableTable().getRowSorter()).isEqualTo(rowSorter.getScrollableTableRowSorter());

    frozenColumnTable.setRowSorter(null);

    assertThat(frozenColumnTable.getRowSorter()).isNull();
    assertThat(frozenColumnTable.getFrozenTable().getRowSorter()).isNull();
    assertThat(frozenColumnTable.getScrollableTable().getRowSorter()).isNull();
  }

  @Test
  public void tableSelection() {
    Object[][] data = new Object[][] {
      new Object[]{"east", "app/src/main/res", false, "east"},
      new Object[]{"west", "app/src/main/res", false, "west"},
      new Object[]{"north", "app/src/main/res", false, "north"}
    };
    Object[] columns = new Object[]{"Key", "Resource Folder", "Untranslatable", "Default Value"};
    DefaultTableModel model = new DefaultTableModel(data, columns);
    FrozenColumnTable<DefaultTableModel> frozenColumnTable = new FrozenColumnTable<>(model, 4);
    frozenColumnTable.getFrozenTable().createDefaultColumnsFromModel();
    frozenColumnTable.getScrollableTable().createDefaultColumnsFromModel();
    JScrollPane pane = (JScrollPane)frozenColumnTable.getScrollPane();
    pane.setBounds(0, 0, 800, 100);
    new FakeUi(pane, 1.0, true);
    pane.doLayout();

    assertEquals(0, frozenColumnTable.getSelectedModelRows().length);

    frozenColumnTable.selectCellAt(0, 0);
    assertThat(frozenColumnTable.getSelectedModelRows()).isEqualTo(new int[] {0});

    frozenColumnTable.selectCellAt(2, 3);
    assertThat(frozenColumnTable.getSelectedModelRows()).isEqualTo(new int[] {2});
  }

  @Test
  public void mouseSelections() {
    Object[][] data = new Object[][] {
      new Object[]{"east", "app/src/main/res", false, "east", "est", "øst"},
      new Object[]{"west", "app/src/main/res", false, "west", "ouest", "vest"},
      new Object[]{"north", "app/src/main/res", false, "north", "nord", "nord"},
      new Object[]{"south", "app/src/main/res", false, "south", "sud", "syd"}
    };
    Object[] columns = new Object[]{"Key", "Resource Folder", "Untranslatable", "Default Value", "French (fr)", "Danish (da)"};
    DefaultTableModel model = new DefaultTableModel(data, columns);
    FrozenColumnTable<DefaultTableModel> frozenColumnTable = new FrozenColumnTable<>(model, 4);
    JTable frozenTable = frozenColumnTable.getFrozenTable();
    frozenTable.setUI(new HeadlessTableUI());
    JTable scrollableTable = frozenColumnTable.getScrollableTable();
    scrollableTable.setUI(new HeadlessTableUI());
    frozenTable.createDefaultColumnsFromModel();
    scrollableTable.createDefaultColumnsFromModel();
    Component panel = frozenColumnTable.getScrollPane();
    panel.setSize(2000, 2000);
    FakeUi ui = new FakeUi(panel, 1.0, true, myRule.getDisposable());

    Point p = getCellLocation(frozenTable, 1, 1, panel);
    ui.mouse.click(p.x, p.y);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{1});

    p = getCellLocation(scrollableTable, 3, 1, panel);
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.mouse.click(p.x, p.y);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{1, 2, 3});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{1, 2, 3, 4, 5});

    p = getCellLocation(scrollableTable, 2, 0, panel);
    ui.mouse.press(p.x, p.y);
    ui.mouse.dragTo(getCellLocation(frozenTable, 1, 3, panel));
    ui.mouse.release();
    assertThat(getRowSelection(frozenColumnTable)).isEqualTo(new int[]{2, 1});
    assertThat(getColumnSelection(frozenColumnTable)).isEqualTo(new int[]{4, 3});
  }

  private static void moveTo(FrozenColumnTable<DefaultTableModel> table, int row, int column) {
    table.getFrozenTable().getSelectionModel().setSelectionInterval(row, row);
    table.gotoColumn(column, false);
  }

  private static Point getCellLocation(JTable table, int row, int column, Component panel) {
    Rectangle cell = table.getCellRect(row, column, false);
    cell = SwingUtilities.convertRectangle(table, cell, panel);
    return new Point((int)cell.getCenterX(), (int)cell.getCenterY());
  }

  private static int[] getRowSelection(FrozenColumnTable<DefaultTableModel> table) {
    int[] frozen = table.getFrozenTable().getSelectionModel().getSelectedIndices();
    int[] scrollable = table.getScrollableTable().getSelectionModel().getSelectedIndices();
    assertThat(frozen).isEqualTo(scrollable);
    if (frozen.length > 0 && table.getFrozenTable().getSelectionModel().getAnchorSelectionIndex() != frozen[0]) {
      frozen = ArrayUtil.reverseArray(frozen);
    }
    return frozen;
  }

  private static int[] getColumnSelection(FrozenColumnTable<DefaultTableModel> table) {
    int[] frozen = table.getFrozenTable().getColumnModel().getSelectionModel().getSelectedIndices();
    int[] scrollable = table.getScrollableTable().getColumnModel().getSelectionModel().getSelectedIndices();
    int[] result = new int[frozen.length + scrollable.length];
    for (int index=0; index < scrollable.length; index++) {
      scrollable[index] += table.getFrozenColumnCount();
    }
    System.arraycopy(frozen, 0, result, 0, frozen.length);
    System.arraycopy(scrollable, 0, result, frozen.length, scrollable.length);
    if (result.length > 0 && table.getAnchorColumn() != result[0]) {
      result = ArrayUtil.reverseArray(result);
    }
    return result;
  }
}
