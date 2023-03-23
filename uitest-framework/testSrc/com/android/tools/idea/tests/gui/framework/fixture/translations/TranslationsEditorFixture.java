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

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;

import com.android.tools.idea.editors.strings.StringResourceEditor;
import com.android.tools.idea.editors.strings.table.FrozenColumnTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MultilineStringEditorDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.SimpleColoredComponent.ColoredIterator;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBLoadingPanel;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;
import org.fest.swing.core.ComponentMatcher;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.DialogMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    GuiTests.waitForBackgroundTasks(myRobot);
    myRobot.waitForIdle();
    JListFixture listFixture = new JListFixture(myRobot, myRobot.finder().find(Matchers.byType(JBList.class)));
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

    table.getFrozenTable().replaceCellWriter(new TranslationsEditorTableCellWriter(myRobot));
    table.getScrollableTable().replaceCellWriter(new TranslationsEditorTableCellWriter(myRobot));

    return table;
  }

  @NotNull
  public List<String> locales() {
    return GuiQuery.get(() -> IntStream.range(StringResourceTableModel.FIXED_COLUMN_COUNT, myTable.getColumnCount())
      .mapToObj(myTable::getColumnName)
      .collect(Collectors.toList()));
  }

  @Nullable
  public TableCell cell(@NotNull String key, @NotNull String resourceFolder, int viewColumnIndex) {
    OptionalInt optionalViewRowIndex = GuiQuery.get(() -> row(key, resourceFolder));

    if (!optionalViewRowIndex.isPresent()) {
      return null;
    }

    return TableCell.row(optionalViewRowIndex.getAsInt()).column(viewColumnIndex);
  }

  @NotNull
  private OptionalInt row(@NotNull String key, @NotNull String resourceFolder) {
    FrozenColumnTable target = getTable().target();

    return IntStream.range(0, target.getRowCount())
      .filter(viewRowIndex -> target.getValueAt(viewRowIndex, StringResourceTableModel.KEY_COLUMN).equals(key) &&
                              target.getValueAt(viewRowIndex, StringResourceTableModel.RESOURCE_FOLDER_COLUMN).equals(resourceFolder))
      .findFirst();
  }

  public void waitUntilTableValueAtEquals(@NotNull TableCell cell, @NotNull Object value) {
    FrozenColumnTableFixture table = getTable();

    // There's nothing special about the 2 second wait, it's simply more than the 500 millisecond delay of the
    // TranslationsEditorTextField.SetTableValueAtTimer
    Wait.seconds(2).expecting("value at " + cell + " to equal " + value).until(() -> table.valueAt(cell).equals(value));
  }

  @NotNull
  public SimpleColoredComponent getCellRenderer(@NotNull TableCell cell) {
    return GuiQuery.get(() -> {
      FrozenColumnTable target = getTable().target();
      int columnCount = target.getFrozenColumnCount();

      JTable table;
      int columnIndex;

      if (cell.column < columnCount) {
        table = target.getFrozenTable();
        columnIndex = cell.column;
      }
      else {
        table = target.getScrollableTable();
        columnIndex = cell.column - columnCount;
      }

      TableCellRenderer renderer = table.getCellRenderer(cell.row, columnIndex);
      return new SimpleColoredComponent((com.intellij.ui.SimpleColoredComponent)table.prepareRenderer(renderer, cell.row, columnIndex));
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
}
