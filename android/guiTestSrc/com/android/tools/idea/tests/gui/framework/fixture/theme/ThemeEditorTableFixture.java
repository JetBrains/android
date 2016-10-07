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
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import org.fest.swing.annotation.RunsInCurrentThread;
import org.fest.swing.core.Robot;
import org.fest.swing.data.TableCell;
import org.fest.swing.driver.BasicJTableCellReader;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.Component;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.fest.swing.edt.GuiActionRunner.execute;

public class ThemeEditorTableFixture extends JTableFixture {
  private ThemeEditorTableFixture(Robot robot, ThemeEditorTable target) {
    super(robot, target);
    replaceCellWriter(new ThemeEditorTableCellWriter(robot));
    replaceCellReader(new BasicJTableCellReader(new ThemeEditorTableCellRendererReader()));
  }

  @NotNull
  public static ThemeEditorTableFixture find(@NotNull Robot robot) {
    return new ThemeEditorTableFixture(robot, robot.finder().findByType(ThemeEditorTable.class));
  }

  @Nullable
  public String attributeNameAt(@NotNull final TableCell cell) {
    return execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        Component renderer = rendererComponentAt(cell);
        if (!(renderer instanceof ResourceComponent)) {
          return null;
        }

        ResourceComponentFixture resourceComponent = new ResourceComponentFixture(robot(), (ResourceComponent)renderer);
        return resourceComponent.getLabelText();
      }
    });
  }

  public boolean hasWarningIconAt(@NotNull final TableCell cell) {
    //noinspection ConstantConditions: this will never return null
    return execute(new GuiQuery<Boolean>() {
      @Override
      protected Boolean executeInEDT() throws Throwable {
        Component renderer = rendererComponentAt(cell);
        if (!(renderer instanceof ResourceComponent)) {
          return false;
        }

        ResourceComponentFixture resourceComponent = new ResourceComponentFixture(robot(), (ResourceComponent)renderer);
        return resourceComponent.hasWarningIcon();
      }
    });
  }

  @Nullable
  public List<String> getComboBoxContentsAt(@NotNull final TableCell cell) {
    return execute(new GuiQuery<List<String>>() {
      @Override
      protected List<String> executeInEDT() throws Throwable {
        Component renderer = checkNotNull(rendererComponentAt(cell));
        JComboBoxFixture comboBox = new JComboBoxFixture(robot(), robot().finder().findByType((JComponent)renderer, JComboBox.class));
        return ImmutableList.copyOf(comboBox.contents());
      }
    });
  }

  @Nullable
  public String getComboBoxSelectionAt(@NotNull final TableCell cell) {
    return execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        Component renderer = checkNotNull(rendererComponentAt(cell));
        JComboBoxFixture comboBox = new JComboBoxFixture(robot(), robot().finder().findByType((JComponent)renderer, JComboBox.class));
        return comboBox.selectedItem();
      }
    });
  }

  @RunsInCurrentThread
  @Nullable
  private Component rendererComponentAt(@NotNull final TableCell cell) {
    return target().prepareRenderer(target().getCellRenderer(cell.row, cell.column), cell.row, cell.column);
  }

  public void requireValueAt(@NotNull final TableCell cell, @Nullable final String value) {
    Wait.minutes(2).expecting("theme editor update").until(() -> Objects.equal(valueAt(cell), value));
  }
}
