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

import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MultilineStringEditorDialogFixture;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.fest.swing.core.ComponentMatcher;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.DialogMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    return newActionButtonFixture("Add Key");
  }

  @NotNull
  public AddKeyDialogFixture getAddKeyDialog() {
    return new AddKeyDialogFixture(myRobot, myRobot.finder().find(DialogMatcher.withTitle("Add Key")));
  }

  public void clickRemoveLocaleItem(@NotNull String text) {
    newActionButtonFixture("Remove Locale").click();
    new JListFixture(myRobot, "localeList").clickItem(text);
  }

  @NotNull
  private ActionButtonFixture newActionButtonFixture(@NotNull String text) {
    GenericTypeMatcher<ActionButton> matcher = new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        return text.equals(button.getAction().getTemplatePresentation().getText());
      }
    };

    return new ActionButtonFixture(myRobot, GuiTests.waitUntilShowingAndEnabled(myRobot, myTranslationsEditor, matcher));
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
  public JTableFixture getTable() {
    return new JTableFixture(myRobot, (JTable)myRobot.finder().findByName(myTranslationsEditor, "table"));
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
      JTable table = getTable().target();

      return IntStream.range(StringResourceTableModel.FIXED_COLUMN_COUNT, table.getColumnCount())
        .mapToObj(table::getColumnName)
        .collect(Collectors.toList());
    });
  }

  @NotNull
  public JTextComponentFixture getKeyTextField() {
    return new JTextComponentFixture(myRobot, (JTextComponent)myRobot.finder().findByName(myTranslationsEditor, "keyTextField"));
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
}
