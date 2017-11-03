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
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.fixture.JTableHeaderFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.JTableHeader;

class FixedColumnTableHeaderFixture extends JTableHeaderFixture {

  final FixedColumnTable myTable;
  final JTableHeaderFixture myFixed;

  public FixedColumnTableHeaderFixture(@NotNull Robot robot, @NotNull JTableHeader target) {
    super(robot, target);
    myTable = (FixedColumnTable)target.getTable();
    myFixed = new JTableHeaderFixture(robot, (JTableHeader)((JScrollPane)myTable.getParent().getParent())
      .getCorner(ScrollPaneConstants.UPPER_LEFT_CORNER));
  }

  private int getFixedColumnCount() {
    return myTable.getTotalColumnCount() - myTable.getColumnCount();
  }

  private boolean isInFixedTable(int column) {
    return column < getFixedColumnCount();
  }

  @NotNull
  @Override
  public JTableHeaderFixture clickColumn(int columnIndex) {
    if (isInFixedTable(columnIndex)) {
      return myFixed.clickColumn(columnIndex);
    }
    return super.clickColumn(columnIndex - getFixedColumnCount());
  }

  @NotNull
  @Override
  public JPopupMenuFixture showPopupMenuAt(int columnIndex) {
    if (isInFixedTable(columnIndex)) {
      return myFixed.showPopupMenuAt(columnIndex);
    }
    return super.showPopupMenuAt(columnIndex - getFixedColumnCount());
  }
}
