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
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.DeleteDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.DialogBuilderFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MultilineStringEditorDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.AddKeyDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.TranslationsEditorFixture;
import com.intellij.notification.Notification;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.EmptyClipboardOwner;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.core.Robot;
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
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import static com.android.tools.idea.editors.strings.table.StringResourceTableModel.*;
import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab.EDITOR;
import static org.junit.Assert.*;
import static com.google.common.truth.Truth.assertThat;

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

    assertEquals(Arrays.asList("app_name", "hello_world", "action_settings", "some_id", "cancel", "app_name"), myTranslationsEditor.keys());

    JTableCellFixture cancel = myTranslationsEditor.getTable().cell(TableCell.row(4).column(CHINESE_IN_CHINA_COLUMN)); // Cancel in zh-rCN
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
    dialog.getResourceFolderComboBox().selectItem(toResourceName("app/src/debug/res"));
    dialog.getOkButton().click();

    Object expected = Arrays.asList("app_name", "hello_world", "action_settings", "some_id", "cancel", "app_name", "action_settings");
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

    dialog.waitUntilErrorLabelFound(toResourceName("action_settings already exists in app/src/main/res"));
    dialog.getCancelButton().click();
  }

  @Test
  public void removeLocale() {
    IdeFrameFixture frame = myGuiTest.ideFrame();

    VirtualFile debugValuesEn = frame.findFileByRelativePath("app/src/debug/res/values-en", false);
    assert debugValuesEn != null;

    VirtualFile mainValuesEn = frame.findFileByRelativePath("app/src/main/res/values-en", false);
    assert mainValuesEn != null;

    myTranslationsEditor.clickRemoveLocaleItem("en");

    Object expected = Arrays.asList("English (en) in United Kingdom (GB)", "Hebrew (iw)", "Tamil (ta)", "Chinese (zh) in China (CN)");
    assertEquals(expected, myTranslationsEditor.locales());

    assertFalse(debugValuesEn.exists());
    assertFalse(mainValuesEn.exists());
  }

  @Test
  public void filterKeys() {
    myTranslationsEditor.clickFilterKeysComboBoxItem("Show Translatable Keys");
    assertEquals(Arrays.asList("app_name", "hello_world", "action_settings", "cancel", "app_name"), myTranslationsEditor.keys());
    myTranslationsEditor.clickFilterKeysComboBoxItem("Show Keys Needing Translations");
    assertEquals(Arrays.asList("app_name", "hello_world", "action_settings", "cancel", "app_name"), myTranslationsEditor.keys());
    myTranslationsEditor.clickFilterKeysComboBoxItem("Show Keys Needing a Translation for English (en)");
    assertEquals(Collections.singletonList("cancel"), myTranslationsEditor.keys());
  }

  @Test
  public void showOnlyHebrew() {
    myTranslationsEditor.clickFilterLocalesComboBoxItem("Show Hebrew (iw)");
    assertEquals(Collections.singletonList("Hebrew (iw)"), myTranslationsEditor.locales());
  }

  @Test
  public void filterByText() {
    myTranslationsEditor.clickFilterKeysComboBoxItem("Filter by Text");

    DialogBuilderFixture dialog = DialogBuilderFixture.find(myGuiTest.robot());
    new JTextComponentFixture(myGuiTest.robot(), myGuiTest.robot().finder().findByType(dialog.target(), JTextField.class))
      .enterText("world");
    dialog.clickOk();

    assertEquals(Collections.singletonList("hello_world"), myTranslationsEditor.keys());
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
  public void goToDeclaration() {
    JTableFixture table = myTranslationsEditor.getTable();
    table.showPopupMenuAt(TableCell.row(5).column(ENGLISH_COLUMN)).menuItemWithPath("Go to Declaration").click();
    assertEquals("<string name=\"app_name\">Simple Application(en)</string>", myGuiTest.ideFrame().getEditor().getCurrentLine().trim());
  }

  @Test
  public void enteringValueUpdatesDebugStringsXml() {
    myTranslationsEditor.getTable().enterValue(TableCell.row(5).column(HEBREW_COLUMN), "app_name_debug_iw");

    Object line = myGuiTest.ideFrame().getEditor()
      .open("app/src/debug/res/values-iw/strings.xml")
      .moveBetween("app_name", "\"")
      .getCurrentLine()
      .trim();

    assertEquals("<string name=\"app_name\">app_name_debug_iw</string>", line);
  }

  @Test
  public void enteringValueUpdatesMainStringsXml() {
    myTranslationsEditor.getTable().enterValue(TableCell.row(0).column(HEBREW_COLUMN), "app_name_main_iw");

    Object line = myGuiTest.ideFrame().getEditor()
      .open("app/src/main/res/values-iw/strings.xml")
      .moveBetween("app_name", "\"")
      .getCurrentLine()
      .trim();

    assertEquals("<string name=\"app_name\">app_name_main_iw</string>", line);
  }

  @Test
  public void rename() {
    myTranslationsEditor.getTable().enterValue(TableCell.row(1).column(KEY_COLUMN), "new_key");
    myGuiTest.waitForBackgroundTasks();

    assertEquals("<string name=\"new_key\">Hello world!</string>", myGuiTest.ideFrame().getEditor()
      .open("app/src/main/res/values/strings.xml")
      .moveBetween("new_key", "\"")
      .getCurrentLine()
      .trim());
    assertEquals("<string name=\"new_key\">Hello world!</string>", myGuiTest.ideFrame().getEditor()
      .open("app/src/main/res/values-en/strings.xml")
      .moveBetween("new_key", "\"")
      .getCurrentLine()
      .trim());
  }

  @Test
  public void resourceFolderColumn() {
    JTableFixture table = myTranslationsEditor.getTable();

    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(0).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(1).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(2).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(3).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(4).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/debug/res"), table.valueAt(TableCell.row(5).column(RESOURCE_FOLDER_COLUMN)));
  }

  @Test
  public void keySorting() {
    JTableFixture table = myTranslationsEditor.getTable();
    Object expected = Arrays.asList("app_name", "hello_world", "action_settings", "some_id", "cancel", "app_name");
    assertEquals(expected, myTranslationsEditor.keys());

    // ascending
    table.tableHeader().clickColumn(0);
    expected = Arrays.asList("action_settings", "app_name", "app_name", "cancel", "hello_world", "some_id");
    assertEquals(expected, myTranslationsEditor.keys());

    // descending
    table.tableHeader().clickColumn(0);
    expected = Arrays.asList("some_id", "hello_world", "cancel", "app_name", "app_name", "action_settings");
    assertEquals(expected, myTranslationsEditor.keys());

    // back to natural order
    table.tableHeader().clickColumn(0);
    expected = Arrays.asList("app_name", "hello_world", "action_settings", "some_id", "cancel", "app_name");
    assertEquals(expected, myTranslationsEditor.keys());
  }

  @Test
  public void enteringTextInDefaultValueTextFieldUpdatesTableCell() {
    JTableFixture table = myTranslationsEditor.getTable();
    TableCell actionSettingsDefaultValue = TableCell.row(2).column(DEFAULT_VALUE_COLUMN);

    table.selectCell(actionSettingsDefaultValue);

    JTextComponentFixture field = myTranslationsEditor.getDefaultValueTextField();

    field.selectAll();
    field.enterText("action_settings");

    TableCell appNameDefaultValue = TableCell.row(0).column(DEFAULT_VALUE_COLUMN);
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
  public void translationTextFieldFocusListenerDoesntThrowIndexOutOfBoundsException() {
    JTableFixture table = myTranslationsEditor.getTable();
    TableCell cell = TableCell.row(0).column(ENGLISH_COLUMN);

    table.selectCell(cell);

    Robot robot = myGuiTest.robot();
    // deselectCell
    robot.pressKey(KeyEvent.VK_CONTROL);
    table.cell(cell).click();
    robot.releaseKey(KeyEvent.VK_CONTROL);

    myTranslationsEditor.getTranslationTextField().focus();
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

  @Test
  public void deleteString() {
    // delete just the translation
    myTranslationsEditor.getTable().selectCell(TableCell.row(1).column(ENGLISH_COLUMN));
    myGuiTest.robot().pressAndReleaseKey(KeyEvent.VK_DELETE);

    EditorFixture editor = myGuiTest.ideFrame().getEditor().open("app/src/main/res/values-en/strings.xml");
    assertThat(editor.getCurrentFileContents()).doesNotContain("hello_world");

    editor.awaitNotification("Edit translations for all locales in the translations editor.")
      .performAction("Open editor");

    // delete the entire string
    myTranslationsEditor.getTable().selectCell(TableCell.row(1).column(KEY_COLUMN));
    myGuiTest.robot().pressAndReleaseKey(KeyEvent.VK_DELETE);
    DeleteDialogFixture.find(myGuiTest.robot()).clickOk().waitForUnsafeDialog().deleteAnyway();

    editor = myGuiTest.ideFrame().getEditor().open("app/src/main/res/values/strings.xml");
    assertThat(editor.getCurrentFileContents()).doesNotContain("hello_world");

    editor = myGuiTest.ideFrame().getEditor().open("app/src/main/res/values-ta/strings.xml");
    assertThat(editor.getCurrentFileContents()).doesNotContain("hello_world");
  }

  @NotNull
  private static KeyStroke getKeyStroke(@NotNull InputMap inputMap, @NotNull Object actionMapKey) {
    Optional<KeyStroke> optionalKeyStroke = Arrays.stream(inputMap.allKeys())
      .filter(keyStroke -> inputMap.get(keyStroke).equals(actionMapKey))
      .findFirst();

    return optionalKeyStroke.orElseThrow(() -> new IllegalArgumentException(actionMapKey.toString()));
  }

  @NotNull
  private static String toResourceName(@NotNull String resName) {
    // Windows and Linux use a different file path separator
    return resName.replace('/', File.separatorChar);
  }
}
