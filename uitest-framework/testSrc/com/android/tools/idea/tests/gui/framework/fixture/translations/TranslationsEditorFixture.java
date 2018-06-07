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
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MultilineStringEditorDialogFixture;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.SimpleColoredComponent.ColoredIterator;
import com.intellij.ui.SimpleTextAttributes;
import org.fest.swing.cell.JTableCellWriter;
import org.fest.swing.core.ComponentMatcher;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.DialogMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.driver.AbstractJTableCellWriter;
import org.fest.swing.driver.JTableCheckBoxEditorCellWriter;
import org.fest.swing.driver.JTableTextComponentEditorCellWriter;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.fest.swing.edt.GuiTask.execute;

public final class TranslationsEditorFixture {
  private final Robot myRobot;
  private final Container myTranslationsEditor;

  private final JButtonFixture myFilterKeysComboBox;

  public TranslationsEditorFixture(@NotNull Robot robot) {
    myRobot = robot;
    myTranslationsEditor = (Container)robot.finder().findByName("translationsEditor");
    myFilterKeysComboBox = getButton("Show All Keys");
  }

  @NotNull
  public ActionButtonFixture getAddKeyButton() {
    GenericTypeMatcher<ActionButton> matcher = new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        return "Add Key".equals(button.getAction().getTemplatePresentation().getText());
      }
    };

    return new ActionButtonFixture(myRobot, GuiTests.waitUntilShowingAndEnabled(myRobot, myTranslationsEditor, matcher));
  }

  @NotNull
  public AddKeyDialogFixture getAddKeyDialog() {
    return new AddKeyDialogFixture(myRobot, myRobot.finder().find(DialogMatcher.withTitle("Add Key")));
  }

  public void clickFilterKeysComboBoxItem(@NotNull String text) {
    clickComboBoxItem(myFilterKeysComboBox, text);
  }

  public void clickFilterLocalesComboBoxItem(@NotNull String text) {
    clickComboBoxItem(getButton("Show All Locales"), text);
  }

  private JButtonFixture getButton(@NotNull String text) {
    ComponentMatcher componentTextEqualsShowAllKeys =
      component -> component instanceof AbstractButton && ((AbstractButton)component).getText().equals(text);

    return new JButtonFixture(myRobot, (JButton)myRobot.finder().find(myTranslationsEditor, componentTextEqualsShowAllKeys));
  }

  private void clickComboBoxItem(@NotNull JButtonFixture button, @NotNull String text) {
    button.click();
    GuiTests.clickPopupMenuItemMatching(t -> t.equals(text), myRobot.finder().findByName(myTranslationsEditor, "toolbar"), myRobot);
  }

  @NotNull
  public FixedColumnTableFixture getTable() {
    FixedColumnTableFixture tableFixture =
      new FixedColumnTableFixture(myRobot, (FixedColumnTable)myRobot.finder().findByName(myTranslationsEditor, "table"));
    tableFixture.replaceCellWriter(new TranslationsEditorTableCellWriter(myRobot));
    return tableFixture;
  }

  @NotNull
  public List<String> keys() {
    JTableFixture table = getTable();

    return IntStream.range(0, table.rowCount())
      .mapToObj(row -> table.valueAt(TableCell.row(row).column(StringResourceTableModel.KEY_COLUMN)))
      .collect(Collectors.toList());
  }

  @NotNull
  public List<String> locales() {
    return GuiQuery.getNonNull(() -> {
      FixedColumnTable table = (FixedColumnTable)getTable().target();
      int start = StringResourceTableModel.FIXED_COLUMN_COUNT - (table.getTotalColumnCount() - table.getColumnCount());
      return IntStream.range(start, table.getColumnCount())
        .mapToObj(table::getColumnName)
        .collect(Collectors.toList());
    });
  }

  public void waitUntilTableValueAtEquals(@NotNull TableCell cell, @NotNull Object value) {
    JTableFixture table = getTable();

    // There's nothing special about the 2 second wait, it's simply more than the 500 millisecond delay of the
    // TranslationsEditorTextField.SetTableValueAtTimer
    Wait.seconds(2).expecting("value at " + cell + " to equal " + value).until(() -> table.valueAt(cell).equals(value));
  }

  @Nullable
  public SimpleColoredComponent getCellRenderer(int row, int column) {
    return GuiActionRunner.execute(new GuiQuery<SimpleColoredComponent>() {
      @Nullable
      @Override
      protected SimpleColoredComponent executeInEDT() {
        JTable table = getTable().target();
        TableCellRenderer renderer = table.getCellRenderer(row, column);

        return new SimpleColoredComponent((com.intellij.ui.SimpleColoredComponent)table.prepareRenderer(renderer, row, column));
      }
    });
  }

  public static final class SimpleColoredComponent {
    public final String myValue;
    public final SimpleTextAttributes myAttributes;
    public final String myTooltipText;

    private SimpleColoredComponent(@NotNull com.intellij.ui.SimpleColoredComponent component) {
      ColoredIterator i = component.iterator();

      myValue = i.next();
      myAttributes = i.getTextAttributes();
      myTooltipText = component.getToolTipText();
    }
  }

  @NotNull
  public JTextComponentFixture getDefaultValueTextField() {
    TextFieldWithBrowseButton field = (TextFieldWithBrowseButton)myRobot.finder().findByName(myTranslationsEditor, "defaultValueTextField");
    return new JTextComponentFixture(myRobot, field.getTextField());
  }

  @NotNull
  public JTextComponentFixture getTranslationTextField() {
    TextFieldWithBrowseButton field = (TextFieldWithBrowseButton)myRobot.finder().findByName(myTranslationsEditor, "translationTextField");
    return new JTextComponentFixture(myRobot, field.getTextField());
  }

  @NotNull
  public MultilineStringEditorDialogFixture getMultilineEditorDialog() {
    TextFieldWithBrowseButton field = (TextFieldWithBrowseButton)myRobot.finder().findByName(myTranslationsEditor, "translationTextField");
    myRobot.click(field.getButton());
    return MultilineStringEditorDialogFixture.find(myRobot);
  }

  /**
   * Sometimes a PSI document is created when a Table Cell finishes its editing - ie stopCellEditing() is called. Users normally do this by
   * pressing enter, changing focus to other cell, etc. Creating documents on the UI thread is not allowed, unless its called inside a
   * performUserActivity().
   * When stopCellEditing() is called from a "User Event", IntelliJ wraps the call with a performUserActivity().
   * When stopCellEditing() is called from FEST, we need to do this ourselves.
   * See IdeEventQueue@startActivity() for the list of AWT UI events classified as "User Events".
   */
  private class TranslationsEditorTableCellWriter extends AbstractJTableCellWriter {
    private final JTableCheckBoxEditorCellWriter checkBoxWriter;
    private final JTableTextComponentEditorCellWriter textComponentWriter;

    public TranslationsEditorTableCellWriter(@NotNull Robot robot) {
      super(robot);
      checkBoxWriter = new JTableCheckBoxEditorCellWriter(robot);
      textComponentWriter = new JTableTextComponentEditorCellWriter(robot) {
        @Override
        public void stopCellEditing(@NotNull JTable table, int row, int column) {
          execute(() -> ((TransactionGuardImpl)TransactionGuard.getInstance()).performUserActivity(table.getCellEditor()::stopCellEditing));
          myRobot.waitForIdle();
        }
      };
    }

    @Override
    public void enterValue(@NotNull JTable table, int row, int column, @NotNull String value) {
      cellWriterFor(table, row, column).enterValue(table, row, column, value);
    }

    @Override
    public void startCellEditing(@NotNull JTable table, int row, int column) {
      cellWriterFor(table, row, column).startCellEditing(table, row, column);
    }

    @Override
    public void stopCellEditing(@NotNull JTable table, int row, int column) {
      cellWriterFor(table, row, column).stopCellEditing(table, row, column);
    }

    @NotNull
    private JTableCellWriter cellWriterFor(@NotNull JTable table, int row, int column) {
      Component editor = editorForCell(table, row, column);
      if (editor instanceof JCheckBox) {
        return checkBoxWriter;
      }
      if (editor instanceof JTextComponent) {
        return textComponentWriter;
      }
      throw cannotFindOrActivateEditor(row, column);
    }

    @Override
    public void cancelCellEditing(@NotNull JTable table, int row, int column) {
      cellWriterFor(table, row, column).cancelCellEditing(table, row, column);
    }
  }
}
