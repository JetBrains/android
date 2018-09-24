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
package com.android.tools.idea.tests.gui.framework.fixture.translations;

import com.android.tools.idea.editors.strings.table.FrozenColumnTable;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.core.Robot;
import org.fest.swing.data.TableCell;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.ObjIntConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class FrozenColumnTableFixture {
  private final FrozenColumnTable myTarget;

  private final JTableFixture myFrozenTable;
  private final JTableFixture myScrollableTable;

  FrozenColumnTableFixture(@NotNull Robot robot, @NotNull FrozenColumnTable target) {
    myTarget = target;
    myFrozenTable = new JTableFixture(robot, target.getFrozenTable());
    myScrollableTable = new JTableFixture(robot, target.getScrollableTable());
  }

  @NotNull
  JTableFixture getScrollableTable() {
    return myScrollableTable;
  }

  @NotNull
  public JTableCellFixture cell(@NotNull TableCell cell) {
    return new TableAndCell(this, cell).apply(JTableFixture::cell);
  }

  @NotNull
  public String valueAt(@NotNull TableCell cell) {
    return new TableAndCell(this, cell).apply(JTableFixture::valueAt);
  }

  public void selectCell(@NotNull TableCell cell) {
    new TableAndCell(this, cell).accept(JTableFixture::selectCell);
  }

  public void pressAndReleaseKey(@NotNull TableCell cell, @NotNull KeyPressInfo key) {
    new TableAndCell(this, cell).accept((table, c) -> table.selectCell(c).pressAndReleaseKey(key));
  }

  @Nullable
  public TableCell selectedCell() {
    return GuiQuery.get(() -> {
      int row = myTarget.getSelectedRow();
      int column = myTarget.getSelectedColumn();

      return row == -1 || column == -1 ? null : TableCell.row(row).column(column);
    });
  }

  public void enterValue(@NotNull TableCell cell, @NotNull String value) {
    new TableAndCell(this, cell).accept((table, c) -> table.enterValue(c, value));
  }

  @NotNull
  public List<String> columnAt(int viewColumnIndex) {
    return GuiQuery.getNonNull(() -> IntStream.range(0, myTarget.getRowCount())
                                              .mapToObj(viewRowIndex -> myTarget.getValueAt(viewRowIndex, viewColumnIndex))
                                              .map(Object::toString)
                                              .collect(Collectors.toList()));
  }

  public int getPreferredColumnWidth(int viewColumnIndex) {
    return GuiQuery.get(() -> {
      JTableFixture table = viewColumnIndex < myTarget.getFrozenColumnCount() ? myFrozenTable : myScrollableTable;
      return table.target().getColumnModel().getColumn(viewColumnIndex).getPreferredWidth();
    });
  }

  public void setPreferredColumnWidth(int viewColumnIndex, int width) {
    GuiTask.execute(() -> {
      JTableFixture table = viewColumnIndex < myTarget.getFrozenColumnCount() ? myFrozenTable : myScrollableTable;
      table.target().getColumnModel().getColumn(viewColumnIndex).setPreferredWidth(width);
    });
  }

  public void clickHeaderColumn(int viewColumnIndex) {
    new TableAndColumn(this, viewColumnIndex).accept((table, c) -> table.tableHeader().clickColumn(c));
  }

  @NotNull
  public JPopupMenuFixture showHeaderPopupMenuAt(int viewColumnIndex) {
    return new TableAndColumn(this, viewColumnIndex).apply((table, c) -> table.tableHeader().showPopupMenuAt(c));
  }

  @NotNull
  public JPopupMenuFixture showPopupMenuAt(@NotNull TableCell cell) {
    return new TableAndCell(this, cell).apply(JTableFixture::showPopupMenuAt);
  }

  private static final class TableAndCell {
    private final JTableFixture myTable;
    private final TableCell myCell;

    private TableAndCell(@NotNull FrozenColumnTableFixture frozenColumnTable, @NotNull TableCell cell) {
      int count = GuiQuery.get(frozenColumnTable.myTarget::getFrozenColumnCount);

      if (cell.column < count) {
        myTable = frozenColumnTable.myFrozenTable;
        myCell = cell;
      }
      else {
        myTable = frozenColumnTable.myScrollableTable;
        myCell = TableCell.row(cell.row).column(cell.column - count);
      }
    }

    private <R> R apply(@NotNull BiFunction<JTableFixture, TableCell, R> function) {
      return function.apply(myTable, myCell);
    }

    private void accept(@NotNull BiConsumer<JTableFixture, TableCell> consumer) {
      consumer.accept(myTable, myCell);
    }
  }

  private static final class TableAndColumn {
    private final JTableFixture myTable;
    private final int myColumn;

    private TableAndColumn(@NotNull FrozenColumnTableFixture frozenColumnTable, int column) {
      int count = GuiQuery.get(frozenColumnTable.myTarget::getFrozenColumnCount);

      if (column < count) {
        myTable = frozenColumnTable.myFrozenTable;
        myColumn = column;
      }
      else {
        myTable = frozenColumnTable.myScrollableTable;
        myColumn = column - count;
      }
    }

    private <R> R apply(@NotNull BiFunction<JTableFixture, Integer, R> function) {
      return function.apply(myTable, myColumn);
    }

    private void accept(@NotNull ObjIntConsumer<JTableFixture> consumer) {
      consumer.accept(myTable, myColumn);
    }
  }

  @NotNull
  public FrozenColumnTable target() {
    return myTarget;
  }
}
