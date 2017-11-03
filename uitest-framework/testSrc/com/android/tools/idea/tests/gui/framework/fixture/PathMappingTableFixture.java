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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.google.common.base.Strings;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.table.TableView;
import org.fest.swing.core.Robot;
import org.fest.swing.data.TableCell;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.TableCellEditor;
import java.awt.Component;
import java.awt.Container;
import java.io.File;

public class PathMappingTableFixture extends JTableFixture {
  @NotNull
  public static PathMappingTableFixture find(@NotNull Robot robot, @NotNull Container ancestor) {
    Component mappingTable = robot.finder().findByName(ancestor, "pathMappingTable", TreeTableView.class);
    return new PathMappingTableFixture(robot, (TreeTableView) mappingTable);
  }

  private static final int VALUE_COL_POSITION = 1;


  private PathMappingTableFixture(@NotNull Robot robot, @NotNull TreeTableView target) {
    super(robot, target);
  }

  private int getUnmappedPathRowPosition() {
    int rowCount = rowCount();
    for(int r = 0; r < rowCount; r++) {
      TableCell valueCoords = TableCell.row(r).column(VALUE_COL_POSITION);
      String localPath = valueAt(valueCoords);
      if (Strings.isNullOrEmpty(localPath)) {
        return r;
      }
    }
    throw new IllegalStateException("No unmapped path found");
  }

  @NotNull
  public PathMappingTableFixture mapRemotePathToLocalPath(@NotNull File localSources) {
    TableCell cellToEdit = TableCell
      .row(getUnmappedPathRowPosition())
      .column(VALUE_COL_POSITION);

    Container editor = (Container) cell(cellToEdit)
      .startEditing()
      .editor();

    Robot robot = robot();
    Wait.seconds(5)
      .expecting("text field to show dialog to enter C/C++ source folder path")
      .until(() -> {
        try {
          // The button's class is FixedSizeButton. This class does not return
          // true for isShowing() when it is showing. Using isVisible() instead
          return getTextFieldToEnterSourcePath(robot, editor).isVisible();
        } catch (ComponentLookupException e) {
          return false;
        }
      });

    robot.enterText(localSources.getAbsolutePath());

    cell(cellToEdit).stopEditing();
    return this;
  }

  private Component getTextFieldToEnterSourcePath(Robot robot, Container cellSourcePathEditor) {
    return robot.finder().findByName(
      cellSourcePathEditor,
      "folderPathCellEditorTextField",
      JTextField.class,
      false);
  }

  @Override
  @NotNull
  public JTableCellFixture cell(@NotNull TableCell cell) {
    return new PathMappingTableCellFixture(this, cell);
  }

  private static class PathMappingTableCellFixture extends JTableCellFixture {
    private final JTableFixture tableFixture;

    public PathMappingTableCellFixture(JTableFixture table, TableCell cell) {
      super(table, cell);
      this.tableFixture = table;
    }

    @NotNull
    @Override
    public JTableCellFixture startEditing() {
      GuiActionRunner.execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          JTable table = tableFixture.target();
          table.editCellAt(row(), column());
        }
      });
      return this;
    }

    @NotNull
    @Override
    public JTableCellFixture stopEditing() {
      GuiActionRunner.execute(new GuiTask() {
        @Override
        protected void executeInEDT() throws Throwable {
          JTable table = tableFixture.target();
          TableCellEditor editor = table.getCellEditor();
          if(editor != null) {
            editor.stopCellEditing();
          }
        }
      });
      return this;
    }
  }
}
