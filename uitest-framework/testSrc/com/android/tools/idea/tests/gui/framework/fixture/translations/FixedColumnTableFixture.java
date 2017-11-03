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
package com.android.tools.idea.tests.gui.framework.fixture.translations;

import com.android.tools.adtui.ui.FixedColumnTable;
import org.fest.swing.core.Robot;
import org.fest.swing.data.TableCell;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.FontFixture;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.fixture.JTableHeaderFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.JTableHeader;

import static org.fest.util.Preconditions.checkNotNull;

public final class FixedColumnTableFixture extends JTableFixture {
  private final JTableFixture myFixed;

  public FixedColumnTableFixture(@NotNull Robot robot, @NotNull FixedColumnTable target) {
    super(robot, target);
    myFixed = new JTableFixture(robot, (JTable)((JScrollPane)target.getParent().getParent()).getRowHeader().getView());
  }

  private int getFixedColumnCount() {
    return myFixed.target().getColumnCount();
  }

  private boolean isInFixedTable(@NotNull TableCell cell) {
    return cell.column < getFixedColumnCount();
  }

  @NotNull
  private TableCell convertToMain(@NotNull TableCell cell) {
    return TableCell.row(cell.row).column(cell.column - getFixedColumnCount());
  }

  @NotNull
  @Override
  public JPopupMenuFixture showPopupMenuAt(@NotNull TableCell cell) {
    if (isInFixedTable(cell)) {
      return myFixed.showPopupMenuAt(cell);
    }
    return super.showPopupMenuAt(convertToMain(cell));
  }

  @NotNull
  @Override
  public JTableFixture enterValue(@NotNull TableCell cell, @NotNull String value) {
    if (isInFixedTable(cell)) {
      return myFixed.enterValue(cell, value);
    }
    return super.enterValue(convertToMain(cell), value);
  }

  @NotNull
  public TableCell selectedCell() {
    return GuiQuery.getNonNull(() -> {
      JTable target = target();

      if (target.getSelectedColumn() == -1) {
        target = myFixed.target();
        return TableCell.row(target.getSelectedRow()).column(target.getSelectedColumn());
      }

      return TableCell.row(target.getSelectedRow()).column(target.getSelectedColumn() + getFixedColumnCount());
    });
  }

  @NotNull
  @Override
  public JTableFixture selectCell(@NotNull TableCell cell) {
    if (isInFixedTable(cell)) {
      return myFixed.selectCell(cell);
    }
    return super.selectCell(convertToMain(cell));
  }

  @Nullable
  @Override
  public String valueAt(@NotNull TableCell cell) {
    if (isInFixedTable(cell)) {
      return myFixed.valueAt(cell);
    }
    return super.valueAt(convertToMain(cell));
  }

  @Override
  public FontFixture fontAt(@NotNull TableCell cell) {
    if (isInFixedTable(cell)) {
      return myFixed.fontAt(cell);
    }
    return super.fontAt(convertToMain(cell));
  }

  @NotNull
  @Override
  public JTableFixture requireCellValue(@NotNull TableCell cell, @Nullable String value) {
    if (isInFixedTable(cell)) {
      return myFixed.requireCellValue(cell, value);
    }
    return super.requireCellValue(convertToMain(cell), value);
  }

  @NotNull
  @Override
  public JTableHeaderFixture tableHeader() {
    JTableHeader tableHeader = driver().tableHeaderOf(target());
    return new FixedColumnTableHeaderFixture(robot(), checkNotNull(tableHeader));
  }
}
