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
import com.intellij.openapi.components.ServiceManager;
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
import org.fest.swing.fixture.JTableCellFixture;
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
  private Path myTamilStringsXmlPath;

  @Before
  public void initPaths() {
    FileSystem fileSystem = FileSystems.getDefault();

    myStringsXmlPath = fileSystem.getPath("app", "src", "main", "res", "values", "strings.xml");
    myTamilStringsXmlPath = fileSystem.getPath("app", "src", "main", "res", "values-ta", "strings.xml");
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

  @Test
  public void basics() throws IOException {
    importSimpleApplication();

    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();
    FrozenColumnTableFixture table = translationsEditor.getTable();

    assertEquals(Arrays.asList("action_settings", "app_name", "app_name", "cancel", "hello_world", "some_id"), table.columnAt(KEY_COLUMN));

    Object expected = Arrays.asList(
      "Chinese (zh) in China (CN)",
      "English (en)",
      "English (en) in United Kingdom (GB)",
      "Hebrew (iw)",
      "Tamil (ta)");
    assertEquals(expected, translationsEditor.locales());

    JTableCellFixture cancel = table.cell(TableCell.row(3).column(CHINESE_IN_CHINA_COLUMN)); // Cancel in zh-rCN
    assertEquals("取消", cancel.value());
    assertEquals(-1, cancel.font().target().canDisplayUpTo("取消")); // requires DroidSansFallbackFull.ttf
  }

  @Test
  public void dialogAddsKeyInDifferentFolder() throws IOException {
    importSimpleApplication();
    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();

    translationsEditor.getAddKeyButton().click();

    AddKeyDialogFixture dialog = translationsEditor.getAddKeyDialog();
    dialog.getDefaultValueTextField().enterText("action_settings");
    dialog.getKeyTextField().enterText("action_settings");
    dialog.getResourceFolderComboBox().selectItem(toResourceName("app/src/debug/res"));
    dialog.getOkButton().click();

    Object expected = Arrays.asList("action_settings", "action_settings", "app_name", "app_name", "cancel", "hello_world", "some_id");
    assertEquals(expected, translationsEditor.getTable().columnAt(KEY_COLUMN));
  }

  @Test
  public void dialogDoesntAddKeyInSameFolder() throws IOException {
    importSimpleApplication();
    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();

    translationsEditor.getAddKeyButton().click();

    AddKeyDialogFixture dialog = translationsEditor.getAddKeyDialog();
    dialog.getDefaultValueTextField().enterText("action_settings");
    dialog.getKeyTextField().enterText("action_settings");
    dialog.getOkButton().click();

    dialog.waitUntilErrorLabelFound(".*" + toResourceName("action_settings already exists in app/src/main/res") + ".*");
    dialog.getCancelButton().click();
  }

  @Test
  public void removeKeyWithDeleteHandler() throws Exception {
    importSimpleApplication();

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    TranslationsEditorFixture translationsEditor = editor.getTranslationsEditor();
    FrozenColumnTableFixture table = translationsEditor.getTable();

    table.pressAndReleaseKey(TableCell.row(4).column(KEY_COLUMN), KeyPressInfo.keyCode(KeyEvent.VK_DELETE));

    DeleteDialogFixture.find(myGuiTest.ideFrame()).unsafeDelete();

    translationsEditor.finishLoading();
    assertEquals(Arrays.asList("action_settings", "app_name", "app_name", "cancel", "some_id"), table.columnAt(KEY_COLUMN));

    editor.open(myStringsXmlPath);
    assertFalse(editor.getCurrentFileContents().contains("hello_world"));

    editor.open(myTamilStringsXmlPath);
    assertFalse(editor.getCurrentFileContents().contains("hello_world"));
  }

  @Test
  public void removeKeyWithSafeDeleteProcessor() throws Exception {
    importSimpleApplication();

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    TranslationsEditorFixture translationsEditor = editor.getTranslationsEditor();
    FrozenColumnTableFixture table = translationsEditor.getTable();

    table.pressAndReleaseKey(TableCell.row(4).column(KEY_COLUMN), KeyPressInfo.keyCode(KeyEvent.VK_DELETE));

    DeleteDialogFixture.find(myGuiTest.ideFrame()).safeDelete().deleteAnyway();

    translationsEditor.finishLoading();
    assertEquals(Arrays.asList("action_settings", "app_name", "app_name", "cancel", "some_id"), table.columnAt(KEY_COLUMN));

    editor.open(myStringsXmlPath);
    assertFalse(editor.getCurrentFileContents().contains("hello_world"));

    editor.open(myTamilStringsXmlPath);
    assertFalse(editor.getCurrentFileContents().contains("hello_world"));
  }

  @Test
  public void filterKeys() throws IOException {
    importSimpleApplication();

    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();
    FrozenColumnTableFixture table = translationsEditor.getTable();

    translationsEditor.clickFilterKeysComboBoxItem("Show Translatable Keys");
    assertEquals(Arrays.asList("action_settings", "app_name", "app_name", "cancel", "hello_world"), table.columnAt(KEY_COLUMN));

    translationsEditor.clickFilterKeysComboBoxItem("Show Keys Needing Translations");
    assertEquals(Arrays.asList("action_settings", "app_name", "app_name", "cancel", "hello_world"), table.columnAt(KEY_COLUMN));

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
    FrozenColumnTableFixture table = myGuiTest.ideFrame().getEditor().getTranslationsEditor().getTable();

    table.selectCell(TableCell.row(1).column(DEFAULT_VALUE_COLUMN));

    String data = "app_name\tapp_name_zh_rcn\n" +
                  "hello_world\thello_world_zh_rcn\n";

    myGuiTest.robot().pasteText(data);
    assertEquals("app_name", table.valueAt(TableCell.row(1).column(DEFAULT_VALUE_COLUMN)));
    assertEquals("app_name_zh_rcn", table.valueAt(TableCell.row(1).column(CHINESE_IN_CHINA_COLUMN)));
    assertEquals("hello_world", table.valueAt(TableCell.row(2).column(DEFAULT_VALUE_COLUMN)));
    assertEquals("hello_world_zh_rcn", table.valueAt(TableCell.row(2).column(CHINESE_IN_CHINA_COLUMN)));
  }

  @Test
  public void goToDeclaration() throws IOException {
    importSimpleApplication();
    EditorFixture editor = myGuiTest.ideFrame().getEditor();

    TableCell cell = TableCell.row(1).column(ENGLISH_COLUMN);
    editor.getTranslationsEditor().getTable().showPopupMenuAt(cell).menuItemWithPath("Go to Declaration").click();

    assertEquals("<string name=\"app_name\">Simple Application(en)</string>", editor.getCurrentLine().trim());
  }

  @Test
  public void enteringValueUpdatesDebugStringsXml() throws IOException {
    importSimpleApplication();
    EditorFixture editor = myGuiTest.ideFrame().getEditor();

    editor.getTranslationsEditor().getTable().enterValue(TableCell.row(1).column(HEBREW_COLUMN), "app_name_debug_iw");

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

    editor.getTranslationsEditor().getTable().enterValue(TableCell.row(2).column(HEBREW_COLUMN), "app_name_main_iw");

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

    editor.getTranslationsEditor().getTable().enterValue(TableCell.row(4).column(KEY_COLUMN), "new_key");
    myGuiTest.waitForBackgroundTasks();

    assertEquals("<string name=\"new_key\">Hello world!</string>", editor
      .open("app/src/main/res/values/strings.xml")
      .moveBetween("new_key", "\"")
      .getCurrentLine()
      .trim());
    assertEquals("<string name=\"new_key\">Hello world!</string>", editor
      .open("app/src/main/res/values-en/strings.xml")
      .moveBetween("new_key", "\"")
      .getCurrentLine()
      .trim());
  }

  @Test
  public void resourceFolderColumn() throws IOException {
    importSimpleApplication();

    FrozenColumnTableFixture table = myGuiTest.ideFrame().getEditor().getTranslationsEditor().getTable();
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(0).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/debug/res"), table.valueAt(TableCell.row(1).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(2).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(3).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(4).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(5).column(RESOURCE_FOLDER_COLUMN)));
  }

  @Test
  public void keySorting() throws IOException {
    importSimpleApplication();
    FrozenColumnTableFixture table = myGuiTest.ideFrame().getEditor().getTranslationsEditor().getTable();

    assertEquals(Arrays.asList("action_settings", "app_name", "app_name", "cancel", "hello_world", "some_id"), table.columnAt(KEY_COLUMN));

    // ascending
    table.clickHeaderColumn(0);
    assertEquals(Arrays.asList("action_settings", "app_name", "app_name", "cancel", "hello_world", "some_id"), table.columnAt(KEY_COLUMN));

    // descending
    table.clickHeaderColumn(0);
    assertEquals(Arrays.asList("some_id", "hello_world", "cancel", "app_name", "app_name", "action_settings"), table.columnAt(KEY_COLUMN));

    // back to natural order
    table.clickHeaderColumn(0);
    assertEquals(Arrays.asList("action_settings", "app_name", "app_name", "cancel", "hello_world", "some_id"), table.columnAt(KEY_COLUMN));
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

    SimpleColoredComponent component = translationsEditor.getCellRenderer(5, DEFAULT_VALUE_COLUMN);
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

    SimpleColoredComponent component = translationsEditor.getCellRenderer(5, ENGLISH_COLUMN);
    assertEquals("https://oslobysykkel.no/_app/options/terms?locale=%1$s&product_id=%2$s", component.myValue);
    assertEquals(SimpleTextAttributes.STYLE_WAVED, component.myAttributes.getStyle());
    assertEquals(JBColor.RED, component.myAttributes.getFgColor());
    assertEquals("Invalid XML", component.myTooltipText);
  }

  @Test
  public void selectedCellIsntLostAfterEnteringValue() throws IOException {
    importSimpleApplication();
    FrozenColumnTableFixture table = myGuiTest.ideFrame().getEditor().getTranslationsEditor().getTable();
    TableCell cell = TableCell.row(1).column(DEFAULT_VALUE_COLUMN);
    table.enterValue(cell, "app_name");

    assertEquals(cell, table.selectedCell());
  }

  @Test
  public void enteringTextInDefaultValueTextFieldUpdatesTableCell() throws IOException {
    importSimpleApplication();

    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();
    FrozenColumnTableFixture table = translationsEditor.getTable();
    TableCell actionSettingsDefaultValue = TableCell.row(0).column(DEFAULT_VALUE_COLUMN);
    table.selectCell(actionSettingsDefaultValue);

    JTextComponentFixture field = translationsEditor.getDefaultValueTextField();
    field.selectAll();
    field.enterText("action_settings");

    TableCell appNameDefaultValue = TableCell.row(1).column(DEFAULT_VALUE_COLUMN);
    table.selectCell(appNameDefaultValue);

    translationsEditor.waitUntilTableValueAtEquals(actionSettingsDefaultValue, "action_settings");
    assertEquals("Simple Application", table.valueAt(appNameDefaultValue));
  }

  @Test
  public void enteringTextInTranslationTextFieldUpdatesTableCell() throws IOException {
    importSimpleApplication();

    TranslationsEditorFixture translationsEditor = myGuiTest.ideFrame().getEditor().getTranslationsEditor();
    TableCell cancelEnglishTranslation = TableCell.row(4).column(ENGLISH_COLUMN);
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

    translationsEditor.getTable().selectCell(TableCell.row(2).column(HEBREW_COLUMN));
    myGuiTest.robot().pasteText("יישום פשוט");

    assertEquals(-1, translationsEditor.getTranslationTextField().font().target().canDisplayUpTo("יישום פשוט"));
  }

  @Test
  public void multilineEditUpdateShowsInTable() throws IOException {
    importSimpleApplication();

    IdeFrameFixture frame = myGuiTest.ideFrame();
    TranslationsEditorFixture translationsEditor = frame.getEditor().getTranslationsEditor();
    FrozenColumnTableFixture table = translationsEditor.getTable();

    table.selectCell(TableCell.row(2).column(ENGLISH_COLUMN));

    /* TODO Ideally, this would be good to have an option on the GuiTestRunner to avoid showing any notification for tests
     * because the notification prevent the robot to click on the multiline editor button */
    Notification notification = ServiceManager.getService(frame.getProject(), AndroidNotification.class).getNotification();

    if (notification != null) {
      notification.hideBalloon();
    }

    MultilineStringEditorDialogFixture editor = translationsEditor.getMultilineEditorDialog();
    editor.getTranslationEditorTextField().replaceText("Multiline\nTest");
    editor.clickOk();

    table.cell(TableCell.row(2).column(ENGLISH_COLUMN)).requireValue("Multiline\nTest");
  }

  @Test
  public void deleteString() throws IOException {
    importSimpleApplication();
    EditorFixture editor = myGuiTest.ideFrame().getEditor();

    // delete just the translation
    editor.getTranslationsEditor().getTable().selectCell(TableCell.row(4).column(ENGLISH_COLUMN));
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

    TableCell cell = TableCell.row(4).column(ENGLISH_COLUMN);
    editor.getTranslationsEditor().getTable().showPopupMenuAt(cell).menuItemWithPath("Delete String(s)").click();

    editor.open("app/src/main/res/values-en/strings.xml");
    assertFalse(editor.getCurrentFileContents().contains("hello_world"));
  }

  @Test
  public void reloadButtonUpdatesEditorFromStringsXml() throws IOException {
    importSimpleApplication();
    EditorFixture editor = myGuiTest.ideFrame().getEditor();

    // Change value to "Reload!"
    editor
      .open("app/src/main/res/values-en/strings.xml")
      .moveBetween("\n", "\n</resources>")
      .enterText("<string name=\"test_reload\">Reload!</string>\n");

    // Switch back to translations editor and click reload
    openTranslationsEditor(myStringsXmlPath);
    TranslationsEditorFixture translationsEditor = editor.getTranslationsEditor();
    translationsEditor.clickReloadButton();

    // Check "Reload!"
    TableCell cell = TableCell.row(6).column(ENGLISH_COLUMN);
    translationsEditor.getTable().selectCell(cell);
    translationsEditor.waitUntilTableValueAtEquals(cell, "Reload!");
  }

  @NotNull
  private static String toResourceName(@NotNull String resName) {
    // Windows and Linux use a different file path separator
    return resName.replace('/', File.separatorChar);
  }
}
