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
import com.intellij.testFramework.DisposableRule;
import com.intellij.testFramework.RuleChain;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import com.intellij.testFramework.ApplicationRule;
import javax.swing.DefaultRowSorter;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import org.junit.Rule;
import org.junit.Test;

public final class FrozenColumnTableTest {
  private final DisposableRule myRule = new DisposableRule();

  @Rule
  public RuleChain chain = new RuleChain(new ApplicationRule(), myRule);

  private static final String NEXT_COLUMN_ACTION = "selectNextColumn";
  private static final String PREVIOUS_COLUMN_ACTION = "selectPreviousColumn";

  @Test
  public void getRowHeight() {
    FrozenColumnTable frozenColumnTable = new FrozenColumnTable(new DefaultTableModel(1, 4), 2);

    JTable frozenTable = frozenColumnTable.getFrozenTable();
    frozenTable.setRowHeight(26);
    frozenTable.getRowHeight();

    JTable scrollableTable = frozenColumnTable.getScrollableTable();
    scrollableTable.setRowHeight(29);
    scrollableTable.getRowHeight();

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
    FrozenColumnTable frozenColumnTable = new FrozenColumnTable(model, 4);
    frozenColumnTable.getFrozenTable().createDefaultColumnsFromModel();
    frozenColumnTable.getScrollableTable().createDefaultColumnsFromModel();
    JScrollPane pane = (JScrollPane)frozenColumnTable.getScrollPane();
    pane.setBounds(0, 0, 800, 20);
    new FakeUi(pane, 1.0, true);
    pane.doLayout();
    assertThat(pane.getVerticalScrollBar().isVisible()).isTrue();
  }

  @Test
  public void tablePreviousNextCanJumpTable() {
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
    new FakeUi(panel, 1.0, true, myRule.getDisposable());
    FakeKeyboardFocusManager focusManager = new FakeKeyboardFocusManager(myRule.getDisposable());
    focusManager.setFocusOwner(frozenColumnTable.getFrozenTable());
    JComponent focusOwner = (JComponent)focusManager.getFocusOwner();
    assertThat(focusOwner).isNotNull();
    frozenTable.changeSelection(1, 3, false, false);

    // Move right jumps to scrollable table:
    focusOwner.getActionMap().get(NEXT_COLUMN_ACTION).actionPerformed(new ActionEvent(focusOwner, 0, NEXT_COLUMN_ACTION));
    focusOwner = (JComponent)focusManager.getFocusOwner();
    assertThat(focusOwner).isSameAs(scrollableTable);
    assertThat(scrollableTable.getSelectedRow()).isEqualTo(1);
    assertThat(scrollableTable.getSelectedColumn()).isEqualTo(0);

    // Move left jumps to frozen table:
    focusOwner.getActionMap().get(PREVIOUS_COLUMN_ACTION).actionPerformed(new ActionEvent(focusOwner, 0, PREVIOUS_COLUMN_ACTION));
    focusOwner = (JComponent)focusManager.getFocusOwner();
    assertThat(focusOwner).isSameAs(frozenTable);
    assertThat(frozenTable.getSelectedRow()).isEqualTo(1);
    assertThat(frozenTable.getSelectedColumn()).isEqualTo(3);
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
    FrozenColumnTable frozenColumnTable = new FrozenColumnTable(model, 4);
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
