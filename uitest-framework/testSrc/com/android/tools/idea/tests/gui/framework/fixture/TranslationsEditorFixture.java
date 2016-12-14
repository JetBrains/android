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
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.google.common.collect.Lists;
import org.fest.swing.core.ComponentFinder;
import org.fest.swing.core.ComponentMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.data.TableCell;
import org.fest.swing.driver.BasicJTableCellReader;
import org.fest.swing.driver.CellRendererReader;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class TranslationsEditorFixture {
  private final Robot myRobot;

  private final JTableFixture myTable;
  private final JTextComponentFixture myKeyTextField;
  private final JTextComponentFixture myTranslationTextField;

  TranslationsEditorFixture(@NotNull Robot robot, @NotNull StringResourceEditor target) {
    myRobot = robot;

    myTable = new JTableFixture(robot, target.getTranslationsTable());
    myTable.replaceCellReader(new BasicJTableCellReader(new StringsCellRendererReader()));

    myKeyTextField = new JTextComponentFixture(robot, target.getKeyTextField());
    myTranslationTextField = new JTextComponentFixture(robot, target.getTranslationTextField().getTextField());
  }

  public void clickFilterKeysComboBoxItem(@NotNull String text) {
    ComponentFinder finder = myRobot.finder();

    ComponentMatcher componentTextEqualsShowAllKeys =
      component -> component instanceof AbstractButton && ((AbstractButton)component).getText().equals("Show All Keys");

    new JButtonFixture(myRobot, (JButton)finder.find(componentTextEqualsShowAllKeys)).click();
    GuiTests.clickPopupMenuItemMatching(t -> t.equals(text), finder.findByName("toolbar"), myRobot);
  }

  @NotNull
  public JTableFixture getTable() {
    return myTable;
  }

  @NotNull
  public JTextComponentFixture getKeyTextField() {
    return myKeyTextField;
  }

  @NotNull
  public JTextComponentFixture getTranslationTextField() {
    return myTranslationTextField;
  }

  @NotNull
  public List<String> locales() {
    List<String> columns = getColumnHeaderValues(myTable.target());
    assert columns.size() > 3 : columns.size();
    return columns.subList(3, columns.size());
  }

  @NotNull
  public List<String> keys() {
    return IntStream.range(0, myTable.rowCount())
      .mapToObj(row -> myTable.valueAt(TableCell.row(row).column(0)))
      .collect(Collectors.toList());
  }

  @NotNull
  private static List<String> getColumnHeaderValues(@NotNull final JTable table) {
    return GuiQuery.getNonNull(
      () -> {
        int columnCount = table.getColumnModel().getColumnCount();
        List<String> columns = Lists.newArrayListWithExpectedSize(columnCount);
        for (int i = 0; i < columnCount; i++) {
          columns.add(table.getColumnName(i));
        }
        return columns;
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
