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
package com.android.tools.idea.tests.gui.editors.translations;

import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.gradle.project.AndroidGradleNotification;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MultilineStringEditorDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.AddKeyDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.TranslationsEditorFixture;
import com.intellij.notification.Notification;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.ui.EmptyClipboardOwner;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import javax.swing.table.TableColumnModel;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import static com.android.tools.idea.editors.strings.table.StringResourceTableModel.*;
import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab.EDITOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(GuiTestRunner.class)
public final class TranslationsEditorTest {
  private static final int ENGLISH_COLUMN = 4;
  private static final int HEBREW_COLUMN = 6;
  private static final int CHINESE_IN_CHINA_COLUMN = 8;

  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  private TranslationsEditorFixture myTranslationsEditor;

  @Before
  public void setUp() throws IOException {
    myGuiTest.importSimpleApplication();
    EditorFixture editor = myGuiTest.ideFrame().getEditor();

    editor.open("app/src/main/res/values/strings.xml", EDITOR)
      .awaitNotification("Edit translations for all locales in the translations editor.")
      .performAction("Open editor");

    GuiTests.waitUntilShowing(myGuiTest.robot(), new GenericTypeMatcher<JTable>(JTable.class) {
      @Override
      protected boolean isMatching(@NotNull JTable table) {
        return table.getModel().getRowCount() != 0;
      }
    });

    myTranslationsEditor = editor.getTranslationsEditor();
  }

  @Test
  public void basics() {
    Object expected = Arrays.asList(
      "English (en)",
      "English (en) in United Kingdom (GB)",
      "Hebrew (iw)",
      "Tamil (ta)",
      "Chinese (zh) in China (CN)");
    assertEquals(expected, myTranslationsEditor.locales());

    assertEquals(Arrays.asList("action_settings", "app_name", "app_name", "cancel", "hello_world"), myTranslationsEditor.keys());

    JTableCellFixture cancel = myTranslationsEditor.getTable().cell(TableCell.row(3).column(CHINESE_IN_CHINA_COLUMN)); // Cancel in zh-rCN
    assertEquals("取消", cancel.value());
    assertEquals(-1, cancel.font().target().canDisplayUpTo("取消")); // requires DroidSansFallbackFull.ttf
  }

  @RunIn(TestGroup.UNRELIABLE)
  @Test
  public void dialogAddsKeyInDifferentFolder() {
    myTranslationsEditor.getAddKeyButton().click();

    AddKeyDialogFixture dialog = myTranslationsEditor.getAddKeyDialog();
    dialog.getDefaultValueTextField().enterText("action_settings");
    dialog.getKeyTextField().enterText("action_settings");
    dialog.getResourceFolderComboBox().selectItem("app/src/debug/res");
    dialog.getOkButton().click();

    Object expected = Arrays.asList("action_settings", "action_settings", "app_name", "app_name", "cancel", "hello_world");
    assertEquals(expected, myTranslationsEditor.keys());
  }

  @RunIn(TestGroup.UNRELIABLE)
  @Test
  public void dialogDoesntAddKeyInSameFolder() {
    myTranslationsEditor.getAddKeyButton().click();

    AddKeyDialogFixture dialog = myTranslationsEditor.getAddKeyDialog();
    dialog.getDefaultValueTextField().enterText("action_settings");
    dialog.getKeyTextField().enterText("action_settings");
    dialog.getOkButton().click();

    dialog.waitUntilErrorLabelFound("action_settings already exists in app/src/main/res");
    dialog.getCancelButton().click();
  }

  @Test
  public void showKeysNeedingTranslationForEnglish() {
    myTranslationsEditor.clickFilterKeysComboBoxItem("Show Keys Needing a Translation for English (en)");
    assertEquals(Arrays.asList("app_name", "cancel"), myTranslationsEditor.keys());
  }

  @Test
  public void showOnlyHebrew() {
    myTranslationsEditor.clickFilterLocalesComboBoxItem("Show Hebrew (iw)");
    assertEquals(Collections.singletonList("Hebrew (iw)"), myTranslationsEditor.locales());
  }

  @Test
  public void setModel() {
    StringResourceTable table = (StringResourceTable)myTranslationsEditor.getTable().target();
    OptionalInt optionalWidth = table.getKeyColumnPreferredWidth();
    TableColumnModel model = table.getColumnModel();

    if (optionalWidth.isPresent()) {
      assertEquals(optionalWidth.getAsInt(), model.getColumn(KEY_COLUMN).getPreferredWidth());
    }
    else {
      fail();
    }

    optionalWidth = table.getDefaultValueAndLocaleColumnPreferredWidths();

    if (optionalWidth.isPresent()) {
      int width = optionalWidth.getAsInt();

      IntStream.range(DEFAULT_VALUE_COLUMN, table.getColumnCount())
        .mapToObj(model::getColumn)
        .forEach(column -> assertEquals(width, column.getPreferredWidth()));
    }
    else {
      fail();
    }
  }

  @Test
  public void paste() {
    JTableFixture table = myTranslationsEditor.getTable();
    table.selectCell(TableCell.row(1).column(DEFAULT_VALUE_COLUMN));

    String data = "app_name\tapp_name_en\n" +
                  "cancel\tcancel_en\n";

    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(data), EmptyClipboardOwner.INSTANCE);

    KeyStroke keyStroke = getKeyStroke(table.target().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT), "paste");
    table.pressAndReleaseKey(KeyPressInfo.keyCode(keyStroke.getKeyCode()).modifiers(keyStroke.getModifiers()));

    assertEquals("app_name", table.valueAt(TableCell.row(1).column(DEFAULT_VALUE_COLUMN)));
    assertEquals("app_name_en", table.valueAt(TableCell.row(1).column(ENGLISH_COLUMN)));
    assertEquals("cancel", table.valueAt(TableCell.row(2).column(DEFAULT_VALUE_COLUMN)));
    assertEquals("cancel_en", table.valueAt(TableCell.row(2).column(ENGLISH_COLUMN)));
  }

  @Test
  public void enteringValueUpdatesDebugStringsXml() {
    myTranslationsEditor.getTable().enterValue(TableCell.row(1).column(HEBREW_COLUMN), "app_name_debug_iw");

    Object line = myGuiTest.ideFrame().getEditor()
      .open("app/src/debug/res/values-iw/strings.xml")
      .moveBetween("app_name", "\"")
      .getCurrentLine()
      .trim();

    assertEquals("<string name=\"app_name\">app_name_debug_iw</string>", line);
  }

  @Test
  public void enteringValueUpdatesMainStringsXml() {
    myTranslationsEditor.getTable().enterValue(TableCell.row(2).column(HEBREW_COLUMN), "app_name_main_iw");

    Object line = myGuiTest.ideFrame().getEditor()
      .open("app/src/main/res/values-iw/strings.xml")
      .moveBetween("app_name", "\"")
      .getCurrentLine()
      .trim();

    assertEquals("<string name=\"app_name\">app_name_main_iw</string>", line);
  }

  @Test
  public void resourceFolderColumn() {
    JTableFixture table = myTranslationsEditor.getTable();

    assertEquals("app/src/main/res", table.valueAt(TableCell.row(0).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals("app/src/debug/res", table.valueAt(TableCell.row(1).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals("app/src/main/res", table.valueAt(TableCell.row(2).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals("app/src/main/res", table.valueAt(TableCell.row(3).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals("app/src/main/res", table.valueAt(TableCell.row(4).column(RESOURCE_FOLDER_COLUMN)));
  }

  @Test
  public void enteringTextInDefaultValueTextFieldUpdatesTableCell() {
    JTableFixture table = myTranslationsEditor.getTable();
    TableCell actionSettingsDefaultValue = TableCell.row(0).column(DEFAULT_VALUE_COLUMN);

    table.selectCell(actionSettingsDefaultValue);

    JTextComponentFixture field = myTranslationsEditor.getDefaultValueTextField();

    field.selectAll();
    field.enterText("action_settings");

    TableCell appNameDefaultValue = TableCell.row(1).column(DEFAULT_VALUE_COLUMN);
    table.selectCell(appNameDefaultValue);

    assertEquals("action_settings", table.valueAt(actionSettingsDefaultValue));
    assertEquals("Simple Application", table.valueAt(appNameDefaultValue));
  }

  @Test
  public void enteringTextInTranslationTextFieldUpdatesTableCell() {
    JTableFixture table = myTranslationsEditor.getTable();
    TableCell cell = TableCell.row(3).column(ENGLISH_COLUMN);

    table.selectCell(cell);

    JTextComponentFixture field = myTranslationsEditor.getTranslationTextField();

    field.selectAll();
    field.enterText("cancel_en");

    // Make the Translation text field lose focus
    myTranslationsEditor.getKeyTextField().focus();

    assertEquals("cancel_en", table.valueAt(cell));
  }

  @Test
  public void translationTextFieldFontCanDisplayPastedHebrew() {
    myTranslationsEditor.getTable().selectCell(TableCell.row(1).column(HEBREW_COLUMN));
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection("יישום פשוט"), EmptyClipboardOwner.INSTANCE);

    JTextComponentFixture translationTextField = myTranslationsEditor.getTranslationTextField();
    KeyStroke keyStroke = getKeyStroke(translationTextField.target().getInputMap(), DefaultEditorKit.pasteAction);
    translationTextField.pressAndReleaseKey(KeyPressInfo.keyCode(keyStroke.getKeyCode()).modifiers(keyStroke.getModifiers()));

    assertEquals(-1, translationTextField.font().target().canDisplayUpTo("יישום פשוט"));
  }

  @Test
  public void multilineEditUpdateShowsInTable() {
    myTranslationsEditor.getTable().selectCell(TableCell.row(1).column(ENGLISH_COLUMN));

    /* TODO Ideally, this would be good to have an option on the GuiTestRunner to avoid showing any notification for tests
     * because the notification prevent the robot to click on the multiline editor button */
    Notification notification =
      ServiceManager.getService(myGuiTest.ideFrame().getProject(), AndroidGradleNotification.class).getNotification();
    if (notification != null) {
      notification.hideBalloon();
    }

    MultilineStringEditorDialogFixture editor = myTranslationsEditor.getMultilineEditorDialog();
    editor.getTranslationEditorTextField().replaceText("Multiline\nTest");
    editor.clickOk();
    myTranslationsEditor.getTable().cell(TableCell.row(1).column(ENGLISH_COLUMN)).requireValue("Multiline\nTest");
  }

  @NotNull
  private static KeyStroke getKeyStroke(@NotNull InputMap inputMap, @NotNull Object actionMapKey) {
    Optional<KeyStroke> optionalKeyStroke = Arrays.stream(inputMap.allKeys())
      .filter(keyStroke -> inputMap.get(keyStroke).equals(actionMapKey))
      .findFirst();

    return optionalKeyStroke.orElseThrow(() -> new IllegalArgumentException(actionMapKey.toString()));
  }
}
