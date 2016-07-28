/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.theme;

import org.fest.swing.core.Robot;
import org.fest.swing.driver.AbstractJTableCellWriter;
import org.fest.swing.driver.JTableLocation;
import org.fest.swing.edt.GuiQuery;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static org.fest.swing.edt.GuiActionRunner.execute;

public class ThemeEditorTableCellWriter extends AbstractJTableCellWriter {
  public ThemeEditorTableCellWriter(@NotNull Robot robot) {
    super(robot);
  }

  /** {@inheritDoc} */
  @Override
  public void enterValue(@NotNull JTable table, int row, int column, @NotNull String value) {}

  /** {@inheritDoc} */
  @Override
  public void startCellEditing(@NotNull final JTable table, final int row, final int column) {
    final JTableLocation location = location();
    execute(new GuiQuery<Void>() {
      @Nullable
      @Override
      protected Void executeInEDT() throws Throwable {
        scrollToCell(table, row, column, location);
        return null;
      }
    });
    Rectangle cellBounds = location.cellBounds(table, row, column);
    robot.click(table, cellBounds.getLocation());
  }
}
