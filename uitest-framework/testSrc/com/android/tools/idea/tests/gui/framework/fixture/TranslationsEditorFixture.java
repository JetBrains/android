/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.idea.editors.strings.StringResourceEditor;
import com.android.tools.idea.editors.strings.table.StringsCellRenderer;
import com.google.common.collect.Lists;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.BasicJTableCellReader;
import org.fest.swing.driver.CellRendererReader;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static org.fest.swing.edt.GuiActionRunner.execute;

public class TranslationsEditorFixture extends JTableFixture {
  public TranslationsEditorFixture(@NotNull Robot robot, @NotNull StringResourceEditor target) {
    super(robot, target.getTranslationsTable());
    replaceCellReader(new BasicJTableCellReader(new StringsCellRendererReader()));
  }

  @NotNull
  public List<String> locales() {
    List<String> columns = getColumnHeaderValues(target());
    assert columns.size() > 3 : "Expected atleast 3 columns (key, default value, isTranslatable?) in the editor, found: " + columns.size();
    return columns.subList(3, columns.size());
  }

  @NotNull
  public List<String> keys() {
    String[][] contents = contents();
    List<String> keys = Lists.newArrayListWithExpectedSize(contents.length);
    for (String[] content : contents) {
      keys.add(content[0]);
    }
    return keys;
  }

  @NotNull
  private static List<String> getColumnHeaderValues(@NotNull final JTable table) {
    //noinspection ConstantConditions
    return execute(new GuiQuery<List<String>>() {
      @Override
      protected List<String> executeInEDT() throws Throwable {
        int columnCount = table.getColumnModel().getColumnCount();
        List<String> columns = Lists.newArrayListWithExpectedSize(columnCount);
        for (int i = 0; i < columnCount; i++) {
          columns.add(table.getColumnName(i));
        }
        return columns;
      }
    });
  }

  private static class StringsCellRendererReader implements CellRendererReader {
    @Nullable
    @Override
    public String valueFrom(Component c) {
      // The toString() method of StringsCellRenderer returns the text that is displayed
      return c instanceof StringsCellRenderer ? c.toString() : null;
    }
  }
}
