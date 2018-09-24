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

import com.android.tools.idea.editors.strings.StringResourceEditor;
import com.android.tools.idea.editors.strings.table.FrozenColumnTable;
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
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBLoadingPanel;
import org.fest.swing.cell.JTableCellWriter;
import org.fest.swing.core.ComponentMatcher;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.DialogMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.driver.AbstractJTableCellWriter;
import org.fest.swing.driver.JTableCheckBoxEditorCellWriter;
import org.fest.swing.driver.JTableTextComponentEditorCellWriter;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static org.fest.swing.edt.GuiTask.execute;

public final class TranslationsEditorFixture {
  private final Robot myRobot;

  private final JBLoadingPanel myLoadingPanel;
  private final JButtonFixture myFilterKeysComboBox;
  private final FrozenColumnTable myTable;

  public TranslationsEditorFixture(@NotNull Robot robot, @NotNull StringResourceEditor editor) {
    myRobot = robot;

    myLoadingPanel = (JBLoadingPanel)robot.finder().findByName("translationsEditor");
    myFilterKeysComboBox = getButton("Show All Keys");
    myTable = editor.getPanel().getTable();
  }

  public void finishLoading() {
    Wait.seconds(10).expecting("translations editor to finish loading").until(() -> !myLoadingPanel.isLoading());
  }

  @NotNull
  public ActionButtonFixture getAddKeyButton() {
    GenericTypeMatcher<ActionButton> matcher = new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        return "Add Key".equals(button.getAction().getTemplatePresentation().getText());
      }
    };

    return new ActionButtonFixture(myRobot, GuiTests.waitUntilShowingAndEnabled(myRobot, myLoadingPanel, matcher));
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

  public void clickReloadButton() {
    GenericTypeMatcher<ActionButton> matcher = new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        return "Reload string resources".equals(button.getAction().getTemplatePresentation().getText());
      }
    };

    new ActionButtonFixture(myRobot, GuiTests.waitUntilShowingAndEnabled(myRobot, myLoadingPanel, matcher)).click();
  }

  public void addNewLocale(@NotNull String newLocale) {
    getAddLocaleButton().click();
    JListFixture listFixture = new JListFixture(myRobot, getLocaleList());
    listFixture.replaceCellReader((jList, index) -> jList.getModel().getElementAt(index).toString());
    listFixture.clickItem(newLocale);
  }

  @NotNull
  private JBList getLocaleList() {
    return waitUntilFound(myRobot, null, new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(@NotNull JBList list) {
        return "localeList".equals(list.getName());
      }
    });
  }

  @NotNull
  private ActionButtonFixture getAddLocaleButton() {
    GenericTypeMatcher<ActionButton> matcher = new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        return "Add Locale".equals(button.getAction().getTemplatePresentation().getText());
      }
    };

    return new ActionButtonFixture(myRobot, GuiTests.waitUntilShowingAndEnabled(myRobot, myLoadingPanel, matcher));
  }

  private JButtonFixture getButton(@NotNull String text) {
    ComponentMatcher componentTextEqualsShowAllKeys =
      component -> component instanceof AbstractButton && ((AbstractButton)component).getText().equals(text);

    return new JButtonFixture(myRobot, (JButton)myRobot.finder().find(myLoadingPanel, componentTextEqualsShowAllKeys));
  }

  private void clickComboBoxItem(@NotNull JButtonFixture button, @NotNull String text) {
    button.click();
    GuiTests.clickPopupMenuItemMatching(t -> t.equals(text), myRobot.finder().findByName(myLoadingPanel, "toolbar"), myRobot);
  }

  @NotNull
  public FrozenColumnTableFixture getTable() {
    FrozenColumnTableFixture table = new FrozenColumnTableFixture(myRobot, myTable);
    table.getScrollableTable().replaceCellWriter(new TranslationsEditorTableCellWriter(myRobot));

    return table;
  }

  @NotNull
  public List<String> locales() {
    return GuiQuery.get(() -> IntStream.range(StringResourceTableModel.FIXED_COLUMN_COUNT, myTable.getColumnCount())
                                       .mapToObj(myTable::getColumnName)
                                       .collect(Collectors.toList()));
  }

  public void waitUntilTableValueAtEquals(@NotNull TableCell cell, @NotNull Object value) {
    FrozenColumnTableFixture table = getTable();

    // There's nothing special about the 2 second wait, it's simply more than the 500 millisecond delay of the
    // TranslationsEditorTextField.SetTableValueAtTimer
    Wait.seconds(2).expecting("value at " + cell + " to equal " + value).until(() -> table.valueAt(cell).equals(value));
  }

  @NotNull
  public SimpleColoredComponent getCellRenderer(int viewRowIndex, int viewColumnIndex) {
    return GuiQuery.get(() -> {
      FrozenColumnTable target = getTable().target();
      int columnCount = target.getFrozenColumnCount();

      JTable table;
      int columnIndex;

      if (viewColumnIndex < columnCount) {
        table = target.getFrozenTable();
        columnIndex = viewColumnIndex;
      }
      else {
        table = target.getScrollableTable();
        columnIndex = viewColumnIndex - columnCount;
      }

      TableCellRenderer renderer = table.getCellRenderer(viewRowIndex, columnIndex);
      return new SimpleColoredComponent((com.intellij.ui.SimpleColoredComponent)table.prepareRenderer(renderer, viewRowIndex, columnIndex));
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
    TextFieldWithBrowseButton field = (TextFieldWithBrowseButton)myRobot.finder().findByName(myLoadingPanel, "defaultValueTextField");
    return new JTextComponentFixture(myRobot, field.getTextField());
  }

  @NotNull
  public JTextComponentFixture getTranslationTextField() {
    TextFieldWithBrowseButton field = (TextFieldWithBrowseButton)myRobot.finder().findByName(myLoadingPanel, "translationTextField");
    return new JTextComponentFixture(myRobot, field.getTextField());
  }

  @NotNull
  public MultilineStringEditorDialogFixture getMultilineEditorDialog() {
    TextFieldWithBrowseButton field = (TextFieldWithBrowseButton)myRobot.finder().findByName(myLoadingPanel, "translationTextField");
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
