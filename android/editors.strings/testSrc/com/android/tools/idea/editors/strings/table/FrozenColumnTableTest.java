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
import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.DisposableRule;
import com.intellij.testFramework.RuleChain;
import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import javax.swing.DefaultRowSorter;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
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
      new Object[]{"north", "app/src/main/res", false, "north", "nord", "nord"}
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
    frozenTable.changeSelection(1, 3, false, false);

    // Move right jumps to scrollable table:
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(scrollableTable.getSelectedRow()).isEqualTo(1);
    assertThat(scrollableTable.getSelectedColumn()).isEqualTo(0);

    // Move left jumps to frozen table:
    ui.keyboard.pressAndRelease(KeyEvent.VK_LEFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(frozenTable.getSelectedRow()).isEqualTo(1);
    assertThat(frozenTable.getSelectedColumn()).isEqualTo(3);

    // Move to end jumps to scrollable table:
    ui.keyboard.pressAndRelease(KeyEvent.VK_END);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(scrollableTable.getSelectedRow()).isEqualTo(1);
    assertThat(scrollableTable.getSelectedColumn()).isEqualTo(1);

    // Move to home jumps to frozen table:
    ui.keyboard.pressAndRelease(KeyEvent.VK_HOME);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(frozenTable.getSelectedRow()).isEqualTo(1);
    assertThat(frozenTable.getSelectedColumn()).isEqualTo(0);

    // Select a range of rows:
    frozenTable.changeSelection(1, 3, false, false);
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_DOWN);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    ListSelectionModel frozenRowModel = frozenTable.getSelectionModel();
    assertThat(frozenRowModel.getSelectedIndices()).isEqualTo(new int[]{1, 2});
    assertThat(frozenRowModel.getLeadSelectionIndex()).isEqualTo(2);
    assertThat(frozenTable.getSelectedColumn()).isEqualTo(3);

    // Move right extending the selection jumps to scrollable table:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_RIGHT);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    ListSelectionModel scrollableRowModel = scrollableTable.getSelectionModel();
    assertThat(scrollableRowModel.getSelectedIndices()).isEqualTo(new int[]{1, 2});
    assertThat(scrollableRowModel.getLeadSelectionIndex()).isEqualTo(2);
    assertThat(scrollableTable.getSelectedColumn()).isEqualTo(0);

    // Move left extending the selection jumps to frozen table:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_LEFT);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(frozenRowModel.getSelectedIndices()).isEqualTo(new int[]{1, 2});
    assertThat(frozenRowModel.getLeadSelectionIndex()).isEqualTo(2);
    assertThat(frozenTable.getSelectedColumn()).isEqualTo(3);

    // Move to end extending the selection jumps to scrollable table:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_END);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(scrollableTable);
    assertThat(scrollableRowModel.getSelectedIndices()).isEqualTo(new int[]{1, 2});
    assertThat(scrollableRowModel.getLeadSelectionIndex()).isEqualTo(2);
    assertThat(scrollableTable.getSelectedColumn()).isEqualTo(1);

    // Move to home jumps to frozen table:
    ui.keyboard.press(KeyEvent.VK_SHIFT);
    ui.keyboard.pressAndRelease(KeyEvent.VK_HOME);
    ui.keyboard.release(KeyEvent.VK_SHIFT);
    assertThat(focusManager.getFocusOwner()).isSameAs(frozenTable);
    assertThat(frozenRowModel.getSelectedIndices()).isEqualTo(new int[]{1, 2});
    assertThat(frozenRowModel.getLeadSelectionIndex()).isEqualTo(2);
    assertThat(frozenTable.getSelectedColumn()).isEqualTo(0);
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
    pane.setBounds(0, 0, 800, 20);
    new FakeUi(pane, 1.0, true);
    pane.doLayout();

    assertEquals(0, frozenColumnTable.getSelectedModelRows().length);

    frozenColumnTable.selectCellAt(0, 0);
    assertThat(frozenColumnTable.getSelectedModelRows()).isEqualTo(new int[] {0});

    frozenColumnTable.selectCellAt(2, 3);
    assertThat(frozenColumnTable.getSelectedModelRows()).isEqualTo(new int[] {2});
  }
}
