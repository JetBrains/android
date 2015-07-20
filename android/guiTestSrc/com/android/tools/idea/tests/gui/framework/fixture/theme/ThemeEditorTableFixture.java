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

import com.android.tools.idea.editors.theme.ThemeEditorTable;
import org.fest.swing.core.Robot;
import org.fest.swing.data.TableCell;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.awt.Component;

import static org.fest.swing.edt.GuiActionRunner.execute;

public class ThemeEditorTableFixture extends JTableFixture {
  private ThemeEditorTableFixture(Robot robot, ThemeEditorTable target) {
    super(robot, target);
  }

  @NotNull
  public static ThemeEditorTableFixture find(@NotNull Robot robot) {
    return new ThemeEditorTableFixture(robot, robot.finder().findByType(ThemeEditorTable.class));
  }

  @Nullable
  public Component getRendererComponent(final TableCell cell) {
    return execute(new GuiQuery<Component>() {
      @Nullable
      @Override
      protected Component executeInEDT() throws Throwable {
        return target().prepareRenderer(target().getCellRenderer(cell.row, cell.column), cell.row, cell.column);
      }
    });
  }
}
