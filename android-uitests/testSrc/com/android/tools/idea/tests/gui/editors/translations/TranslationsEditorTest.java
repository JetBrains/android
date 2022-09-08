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

import static com.android.tools.idea.editors.strings.table.StringResourceTableModel.DEFAULT_VALUE_COLUMN;
import static com.android.tools.idea.editors.strings.table.StringResourceTableModel.KEY_COLUMN;
import static com.android.tools.idea.editors.strings.table.StringResourceTableModel.RESOURCE_FOLDER_COLUMN;
import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.DeleteDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.DialogBuilderFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorNotificationPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MultilineStringEditorDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.AddKeyDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.FrozenColumnTableFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.TranslationsEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.TranslationsEditorFixture.SimpleColoredComponent;
import com.intellij.notification.Notification;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.JTextField;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public final class TranslationsEditorTest {
  private static final int CHINESE_IN_CHINA_COLUMN = 4;
  private static final int ENGLISH_COLUMN = 5;
  private static final int HEBREW_COLUMN = 7;

  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  private Path myStringsXmlPath;

  @Before
  public void initPaths() {
    FileSystem fileSystem = FileSystems.getDefault();

    myStringsXmlPath = fileSystem.getPath("app", "src", "main", "res", "values", "strings.xml");
  }

  private void importSimpleApplication() throws IOException {
    myGuiTest.importSimpleApplication();
    openTranslationsEditor(myStringsXmlPath);
  }

  private void openTranslationsEditor(@NotNull Path path) {
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open(path, Tab.EDITOR);

    EditorNotificationPanelFixture panel = editor.awaitNotification("Edit translations for all locales in the translations editor.");
    panel.performAction("Open editor");

    editor.getTranslationsEditor().finishLoading();
  }

  // TODO(b/232444069): Test that filters work at the table level and remove these tests.
  @Test
  public void filterKeys() throws IOException {
    importSimpleApplication();

    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();
    FrozenColumnTableFixture table = translationsEditor.getTable();

    translationsEditor.clickFilterKeysComboBoxItem("Show Translatable Keys");
    assertEquals(Arrays.asList("app_name", "app_name", "hello_world", "action_settings", "cancel"), table.columnAt(KEY_COLUMN));

    translationsEditor.clickFilterKeysComboBoxItem("Show Keys Needing Translations");
    assertEquals(Arrays.asList("app_name", "app_name", "hello_world", "action_settings", "cancel"), table.columnAt(KEY_COLUMN));

    translationsEditor.clickFilterKeysComboBoxItem("Show Keys Needing a Translation for English (en)");
    assertEquals(Collections.singletonList("cancel"), table.columnAt(KEY_COLUMN));
  }

  @Test
  public void showOnlyHebrew() throws IOException {
    importSimpleApplication();
    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();

    translationsEditor.clickFilterLocalesComboBoxItem("Show Hebrew (iw)");
    assertEquals(Collections.singletonList("Hebrew (iw)"), translationsEditor.locales());
  }

  @Test
  public void filterByText() throws IOException {
    importSimpleApplication();
    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();

    translationsEditor.clickFilterKeysComboBoxItem("Filter by Text");

    DialogBuilderFixture dialog = DialogBuilderFixture.find(myGuiTest.robot());
    new JTextComponentFixture(myGuiTest.robot(), myGuiTest.robot().finder().findByType(dialog.target(), JTextField.class))
      .enterText("world");
    dialog.clickOk();

    assertEquals(Collections.singletonList("hello_world"), translationsEditor.getTable().columnAt(KEY_COLUMN));
  }

  @Test
  public void paste() throws IOException {
    importSimpleApplication();

    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();
    FrozenColumnTableFixture table = translationsEditor.getTable();
    TableCell appNameDefaultValue = translationsEditor.cell("app_name", "app/src/main/res", DEFAULT_VALUE_COLUMN);

    table.selectCell(appNameDefaultValue);

    String data = "app_name\tapp_name_zh_rcn\n" +
                  "hello_world\thello_world_zh_rcn\n";

    myGuiTest.robot().pasteText(data);
    assertEquals("app_name", table.valueAt(appNameDefaultValue));
    assertEquals("app_name_zh_rcn", table.valueAt(translationsEditor.cell("app_name", "app/src/main/res", CHINESE_IN_CHINA_COLUMN)));
    assertEquals("hello_world", table.valueAt(translationsEditor.cell("hello_world", "app/src/main/res", DEFAULT_VALUE_COLUMN)));
    assertEquals("hello_world_zh_rcn", table.valueAt(translationsEditor.cell("hello_world", "app/src/main/res", CHINESE_IN_CHINA_COLUMN)));
  }

  @Test
  public void goToDeclaration() throws IOException {
    importSimpleApplication();

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    TranslationsEditorFixture translationsEditor = editor.getTranslationsEditor();
    TableCell appNameEnglish = translationsEditor.cell("app_name", "app/src/main/res", ENGLISH_COLUMN);

    translationsEditor.getTable().showPopupMenuAt(appNameEnglish).menuItemWithPath("Go to Declaration").click();

    assertEquals("<string name=\"app_name\">Simple Application</string>", editor.getCurrentLine().trim());
  }

  @Test
  public void enteringValueUpdatesDebugStringsXml() throws IOException {
    importSimpleApplication();

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    TranslationsEditorFixture translationsEditor = editor.getTranslationsEditor();

    translationsEditor.getTable().enterValue(translationsEditor.cell("app_name", "app/src/debug/res", HEBREW_COLUMN), "app_name_debug_iw");

    Object line = editor
      .open("app/src/debug/res/values-iw/strings.xml")
      .moveBetween("app_name", "\"")
      .getCurrentLine()
      .trim();

    assertEquals("<string name=\"app_name\">app_name_debug_iw</string>", line);
  }

  @Test
  public void enteringValueUpdatesMainStringsXml() throws IOException {
    importSimpleApplication();

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    TranslationsEditorFixture translationsEditor = editor.getTranslationsEditor();

    translationsEditor.getTable().enterValue(translationsEditor.cell("app_name", "app/src/main/res", HEBREW_COLUMN), "app_name_main_iw");

    Object line = editor
      .open("app/src/main/res/values-iw/strings.xml")
      .moveBetween("app_name", "\"")
      .getCurrentLine()
      .trim();

    assertEquals("<string name=\"app_name\">app_name_main_iw</string>", line);
  }

  @Test
  public void rename() throws IOException {
    importSimpleApplication();

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    TranslationsEditorFixture translationsEditor = editor.getTranslationsEditor();

    translationsEditor.getTable().enterValue(translationsEditor.cell("hello_world", "app/src/main/res", KEY_COLUMN), "new_key");
    myGuiTest.waitForBackgroundTasks();

    String contents = editor.open("app/src/main/res/values/strings.xml", Tab.EDITOR).getCurrentFileContents();
    assertTrue(contents.contains("<string name=\"new_key\">Hello world!</string>"));

    contents = editor.open("app/src/main/res/values-en/strings.xml", Tab.EDITOR).getCurrentFileContents();
    assertTrue(contents.contains("<string name=\"new_key\">Hello world!</string>"));
  }

  @Test
  public void resourceFolderColumn() throws IOException {
    importSimpleApplication();

    Object expectedColumn = Arrays.asList(
      "app/src/debug/res",
      "app/src/main/res",
      "app/src/main/res",
      "app/src/main/res",
      "app/src/main/res",
      "app/src/main/res");

    assertEquals(expectedColumn, myGuiTest.ideFrame().getEditor().getTranslationsEditor().getTable().columnAt(RESOURCE_FOLDER_COLUMN));
  }

  @Test
  public void keyColumnWidthDoesntResetWhenAddingKey() throws IOException {
    importSimpleApplication();
    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();

    // A new width not equal to the current one
    int width = 127;

    FrozenColumnTableFixture table = translationsEditor.getTable();
    table.setPreferredColumnWidth(KEY_COLUMN, width);

    translationsEditor.getAddKeyButton().click();

    AddKeyDialogFixture dialog = translationsEditor.getAddKeyDialog();
    dialog.getDefaultValueTextField().enterText("key_1");
    dialog.getKeyTextField().enterText("key_1");
    dialog.getOkButton().click();

    assertEquals(width, table.getPreferredColumnWidth(KEY_COLUMN));
  }

  @Test
  public void invalidDefaultValueXml() throws IOException {
    EditorFixture editor = myGuiTest.importSimpleApplication().getEditor();

    editor
      .open(myStringsXmlPath, Tab.EDITOR)
      .moveBetween("</string>\n", "    <string-array name=\"my_array\">")
      .enterText(
        "    <string name=\"oslo_bysykkel_terms_url\">https://oslobysykkel.no/_app/options/terms?locale=%1$s&product_id=%2$s</string>\n")
      .awaitNotification("Edit translations for all locales in the translations editor.")
      .performAction("Open editor");

    TranslationsEditorFixture translationsEditor = editor.getTranslationsEditor();
    translationsEditor.finishLoading();

    SimpleColoredComponent component =
      translationsEditor.getCellRenderer(translationsEditor.cell("oslo_bysykkel_terms_url", "app/src/main/res", DEFAULT_VALUE_COLUMN));

    assertEquals("https://oslobysykkel.no/_app/options/terms?locale=%1$s&product_id=%2$s", component.myValue);
    assertEquals(SimpleTextAttributes.STYLE_WAVED, component.myAttributes.getStyle());
    assertEquals(JBColor.RED, component.myAttributes.getFgColor());
    assertEquals("Invalid XML", component.myTooltipText);
  }

  @Test
  public void invalidTranslationXml() throws IOException {
    EditorFixture editor = myGuiTest.importSimpleApplication().getEditor();

    editor
      .open(Paths.get("app", "src", "main", "res", "values-en", "strings.xml"), Tab.EDITOR)
      .moveBetween("</string>\n", "\n")
      .enterText(
        "    <string name=\"oslo_bysykkel_terms_url\">https://oslobysykkel.no/_app/options/terms?locale=%1$s&product_id=%2$s</string>")
      .awaitNotification("Edit translations for all locales in the translations editor.")
      .performAction("Open editor");

    TranslationsEditorFixture translationsEditor = editor.getTranslationsEditor();
    translationsEditor.finishLoading();

    SimpleColoredComponent component =
      translationsEditor.getCellRenderer(translationsEditor.cell("oslo_bysykkel_terms_url", "app/src/main/res", ENGLISH_COLUMN));

    assertEquals("https://oslobysykkel.no/_app/options/terms?locale=%1$s&product_id=%2$s", component.myValue);
    assertEquals(SimpleTextAttributes.STYLE_WAVED, component.myAttributes.getStyle());
    assertEquals(JBColor.RED, component.myAttributes.getFgColor());
    assertEquals("Invalid XML", component.myTooltipText);
  }

  @Test
  public void selectedCellIsntLostAfterEnteringValue() throws IOException {
    importSimpleApplication();

    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();
    FrozenColumnTableFixture table = translationsEditor.getTable();
    TableCell cell = translationsEditor.cell("app_name", "app/src/main/res", DEFAULT_VALUE_COLUMN);

    table.enterValue(cell, "app_name");

    assertEquals(cell, table.selectedCell());
  }

  @Test
  public void enteringTextInDefaultValueTextFieldUpdatesTableCell() throws IOException {
    importSimpleApplication();

    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();
    FrozenColumnTableFixture table = translationsEditor.getTable();
    TableCell actionSettingsDefaultValue = translationsEditor.cell("action_settings", "app/src/main/res", DEFAULT_VALUE_COLUMN);
    table.selectCell(actionSettingsDefaultValue);

    JTextComponentFixture field = translationsEditor.getDefaultValueTextField();
    field.selectAll();
    field.enterText("action_settings");

    TableCell appNameDefaultValue = translationsEditor.cell("app_name", "app/src/main/res", DEFAULT_VALUE_COLUMN);
    table.selectCell(appNameDefaultValue);

    translationsEditor.waitUntilTableValueAtEquals(actionSettingsDefaultValue, "action_settings");
    assertEquals("Simple Application", table.valueAt(appNameDefaultValue));
  }

  @Test
  public void enteringTextInTranslationTextFieldUpdatesTableCell() throws IOException {
    importSimpleApplication();

    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();
    TableCell cancelEnglishTranslation = translationsEditor.cell("cancel", "app/src/main/res", ENGLISH_COLUMN);
    translationsEditor.getTable().selectCell(cancelEnglishTranslation);

    JTextComponentFixture field = translationsEditor.getTranslationTextField();
    field.selectAll();
    field.enterText("cancel_en");

    translationsEditor.waitUntilTableValueAtEquals(cancelEnglishTranslation, "cancel_en");
  }

  @Test
  public void translationTextFieldFontCanDisplayPastedHebrew() throws IOException {
    importSimpleApplication();
    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();

    translationsEditor.getTable().selectCell(translationsEditor.cell("hello_world", "app/src/main/res", HEBREW_COLUMN));
    myGuiTest.robot().pasteText("יישום פשוט");

    assertEquals(-1, translationsEditor.getTranslationTextField().font().target().canDisplayUpTo("יישום פשוט"));
  }

  @Test
  public void multilineEditUpdateShowsInTable() throws IOException {
    importSimpleApplication();

    IdeFrameFixture frame = myGuiTest.ideFrame();
    TranslationsEditorFixture translationsEditor = frame.getEditor().getTranslationsEditor();
    FrozenColumnTableFixture table = translationsEditor.getTable();

    TableCell helloWorldEnglish = translationsEditor.cell("hello_world", "app/src/main/res", ENGLISH_COLUMN);
    table.selectCell(helloWorldEnglish);

    /* TODO Ideally, this would be good to have an option on the GuiTestRunner to avoid showing any notification for tests
     * because the notification prevent the robot to click on the multiline editor button */
    Notification notification = frame.getProject().getService(AndroidNotification.class).getNotification();

    if (notification != null) {
      notification.hideBalloon();
    }

    MultilineStringEditorDialogFixture editor = translationsEditor.getMultilineEditorDialog();
    editor.getTranslationEditorTextField().replaceText("Multiline\nTest");
    editor.clickOk();

    table.cell(helloWorldEnglish).requireValue("Multiline\nTest");
  }

  @Test
  public void deleteString() throws IOException {
    importSimpleApplication();

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    TranslationsEditorFixture translationsEditor = editor.getTranslationsEditor();

    // delete just the translation
    translationsEditor.getTable().selectCell(translationsEditor.cell("hello_world", "app/src/main/res", ENGLISH_COLUMN));
    myGuiTest.robot().pressAndReleaseKey(KeyEvent.VK_DELETE);

    // gone from en
    editor.open("app/src/main/res/values-en/strings.xml");
    assertFalse(editor.getCurrentFileContents().contains("hello_world"));

    // still in other languages
    editor.open("app/src/main/res/values-ta/strings.xml");
    assertTrue(editor.getCurrentFileContents().contains("hello_world"));
  }

  @Test
  public void deleteRightClick() throws IOException {
    importSimpleApplication();

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    TranslationsEditorFixture translationsEditor = editor.getTranslationsEditor();
    TableCell helloWorldEnglish = translationsEditor.cell("hello_world", "app/src/main/res", ENGLISH_COLUMN);

    translationsEditor.getTable().showPopupMenuAt(helloWorldEnglish).menuItemWithPath("Delete String(s)").click();

    editor.open("app/src/main/res/values-en/strings.xml");
    assertFalse(editor.getCurrentFileContents().contains("hello_world"));
  }
}
