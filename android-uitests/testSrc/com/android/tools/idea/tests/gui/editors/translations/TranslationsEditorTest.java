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

import com.android.tools.adtui.ui.FixedColumnTable;
import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.translations.AddKeyDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.FixedColumnTableFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.TranslationsEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.translations.TranslationsEditorFixture.SimpleColoredComponent;
import com.android.tools.idea.tests.util.GuiTestFileUtils;
import com.android.tools.idea.tests.util.WizardUtils;
import com.intellij.notification.Notification;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.EmptyClipboardOwner;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.data.TableCell;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import javax.swing.table.TableColumn;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import static com.android.tools.idea.editors.strings.table.StringResourceTableModel.*;
import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;

@RunWith(GuiTestRunner.class)
public final class TranslationsEditorTest {
  private static final int CHINESE_IN_CHINA_COLUMN = 4;
  private static final int ENGLISH_COLUMN = 5;
  private static final int HEBREW_COLUMN = 7;

  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  private TranslationsEditorFixture myTranslationsEditor;

  private void importSimpleApplication() throws IOException {
    myGuiTest.importSimpleApplication();
    openTranslationsEditor(FileSystems.getDefault().getPath("app", "src", "main", "res", "values", "strings.xml"));
  }

  private void openTranslationsEditor(@NotNull Path path) {
    EditorFixture editor = myGuiTest.ideFrame().getEditor();

    EditorNotificationPanelFixture notificationPanel = editor.open(path, Tab.EDITOR)
      .awaitNotification("Edit translations for all locales in the translations editor.");

    notificationPanel.performAction("Open editor");

    GuiTests.waitUntilShowing(myGuiTest.robot(), new GenericTypeMatcher<FixedColumnTable>(FixedColumnTable.class) {
      @Override
      protected boolean isMatching(@NotNull FixedColumnTable table) {
        return table.getModel().getRowCount() != 0;
      }
    });

    myTranslationsEditor = editor.getTranslationsEditor();
  }

  @Test
  public void basics() throws IOException {
    importSimpleApplication();

    Object expected = Arrays.asList(
      "Chinese (zh) in China (CN)",
      "English (en)",
      "English (en) in United Kingdom (GB)",
      "Hebrew (iw)",
      "Tamil (ta)");
    assertEquals(expected, myTranslationsEditor.locales());

    assertEquals(Arrays.asList("app_name", "hello_world", "action_settings", "some_id", "cancel", "app_name"), myTranslationsEditor.keys());

    JTableCellFixture cancel = myTranslationsEditor.getTable().cell(TableCell.row(4).column(CHINESE_IN_CHINA_COLUMN)); // Cancel in zh-rCN
    assertEquals("取消", cancel.value());
    assertEquals(-1, cancel.font().target().canDisplayUpTo("取消")); // requires DroidSansFallbackFull.ttf
  }

  @Test
  public void dialogAddsKeyInDifferentFolder() throws IOException {
    importSimpleApplication();
    myTranslationsEditor.getAddKeyButton().click();

    AddKeyDialogFixture dialog = myTranslationsEditor.getAddKeyDialog();
    dialog.getDefaultValueTextField().enterText("action_settings");
    dialog.getKeyTextField().enterText("action_settings");
    dialog.getResourceFolderComboBox().selectItem(toResourceName("app/src/debug/res"));
    dialog.getOkButton().click();

    Object expected = Arrays.asList("app_name", "hello_world", "action_settings", "some_id", "cancel", "app_name", "action_settings");
    assertEquals(expected, myTranslationsEditor.keys());
  }

  @Test
  public void dialogDoesntAddKeyInSameFolder() throws IOException {
    importSimpleApplication();
    myTranslationsEditor.getAddKeyButton().click();

    AddKeyDialogFixture dialog = myTranslationsEditor.getAddKeyDialog();
    dialog.getDefaultValueTextField().enterText("action_settings");
    dialog.getKeyTextField().enterText("action_settings");
    dialog.getOkButton().click();

    dialog.waitUntilErrorLabelFound(toResourceName("action_settings already exists in app/src/main/res"));
    dialog.getCancelButton().click();
  }

  @Test
  public void removeLocale() throws IOException {
    importSimpleApplication();
    IdeFrameFixture frame = myGuiTest.ideFrame();

    VirtualFile debugValuesEn = frame.findFileByRelativePath("app/src/debug/res/values-en", false);
    assert debugValuesEn != null;

    VirtualFile mainValuesEn = frame.findFileByRelativePath("app/src/main/res/values-en", false);
    assert mainValuesEn != null;

    myTranslationsEditor.getTable().tableHeader().showPopupMenuAt(ENGLISH_COLUMN).menuItem("removeLocaleMenuItem").click();

    Object expected = Arrays.asList(
      "Chinese (zh) in China (CN)",
      "English (en) in United Kingdom (GB)",
      "Hebrew (iw)",
      "Tamil (ta)");

    assertEquals(expected, myTranslationsEditor.locales());

    assertFalse(debugValuesEn.exists());
    assertFalse(mainValuesEn.exists());
  }

  @Test
  public void filterKeys() throws IOException {
    importSimpleApplication();

    myTranslationsEditor.clickFilterKeysComboBoxItem("Show Translatable Keys");
    assertEquals(Arrays.asList("app_name", "hello_world", "action_settings", "cancel", "app_name"), myTranslationsEditor.keys());
    myTranslationsEditor.clickFilterKeysComboBoxItem("Show Keys Needing Translations");
    assertEquals(Arrays.asList("app_name", "hello_world", "action_settings", "cancel", "app_name"), myTranslationsEditor.keys());
    myTranslationsEditor.clickFilterKeysComboBoxItem("Show Keys Needing a Translation for English (en)");
    assertEquals(Collections.singletonList("cancel"), myTranslationsEditor.keys());
  }

  @Test
  public void showOnlyHebrew() throws IOException {
    importSimpleApplication();

    myTranslationsEditor.clickFilterLocalesComboBoxItem("Show Hebrew (iw)");
    assertEquals(Collections.singletonList("Hebrew (iw)"), myTranslationsEditor.locales());
  }

  @Test
  public void filterByText() throws IOException {
    importSimpleApplication();
    myTranslationsEditor.clickFilterKeysComboBoxItem("Filter by Text");

    DialogBuilderFixture dialog = DialogBuilderFixture.find(myGuiTest.robot());
    new JTextComponentFixture(myGuiTest.robot(), myGuiTest.robot().finder().findByType(dialog.target(), JTextField.class))
      .enterText("world");
    dialog.clickOk();

    assertEquals(Collections.singletonList("hello_world"), myTranslationsEditor.keys());
  }

  @Test
  public void setModel() throws IOException {
    importSimpleApplication();

    StringResourceTable table = (StringResourceTable)myTranslationsEditor.getTable().target();
    OptionalInt optionalWidth = table.getKeyColumnPreferredWidth();

    if (optionalWidth.isPresent()) {
      assertEquals(optionalWidth.getAsInt(), table.getColumn(KEY_COLUMN).getPreferredWidth());
    }
    else {
      fail();
    }

    optionalWidth = table.getDefaultValueAndLocaleColumnPreferredWidths();

    if (optionalWidth.isPresent()) {
      int width = optionalWidth.getAsInt();

      IntStream.range(DEFAULT_VALUE_COLUMN, table.getColumnCount())
        .mapToObj(table::getColumn)
        .forEach(column -> assertEquals(width, column.getPreferredWidth()));
    }
    else {
      fail();
    }
  }

  @Test
  public void paste() throws IOException {
    importSimpleApplication();

    JTableFixture table = myTranslationsEditor.getTable();
    table.selectCell(TableCell.row(0).column(DEFAULT_VALUE_COLUMN));

    String data = "app_name\tapp_name_zh_rcn\n" +
                  "hello_world\thello_world_zh_rcn\n";

    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(data), EmptyClipboardOwner.INSTANCE);

    KeyStroke keyStroke = getKeyStroke(table.target().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT), "paste");
    table.pressAndReleaseKey(KeyPressInfo.keyCode(keyStroke.getKeyCode()).modifiers(keyStroke.getModifiers()));

    assertEquals("app_name", table.valueAt(TableCell.row(0).column(DEFAULT_VALUE_COLUMN)));
    assertEquals("app_name_zh_rcn", table.valueAt(TableCell.row(0).column(CHINESE_IN_CHINA_COLUMN)));
    assertEquals("hello_world", table.valueAt(TableCell.row(1).column(DEFAULT_VALUE_COLUMN)));
    assertEquals("hello_world_zh_rcn", table.valueAt(TableCell.row(1).column(CHINESE_IN_CHINA_COLUMN)));
  }

  @Test
  public void goToDeclaration() throws IOException {
    importSimpleApplication();

    JTableFixture table = myTranslationsEditor.getTable();
    table.showPopupMenuAt(TableCell.row(5).column(ENGLISH_COLUMN)).menuItemWithPath("Go to Declaration").click();
    assertEquals("<string name=\"app_name\">Simple Application(en)</string>", myGuiTest.ideFrame().getEditor().getCurrentLine().trim());
  }

  @Test
  public void enteringValueUpdatesDebugStringsXml() throws IOException {
    importSimpleApplication();
    myTranslationsEditor.getTable().enterValue(TableCell.row(5).column(HEBREW_COLUMN), "app_name_debug_iw");

    Object line = myGuiTest.ideFrame().getEditor()
      .open("app/src/debug/res/values-iw/strings.xml")
      .moveBetween("app_name", "\"")
      .getCurrentLine()
      .trim();

    assertEquals("<string name=\"app_name\">app_name_debug_iw</string>", line);
  }

  @Test
  public void enteringValueUpdatesMainStringsXml() throws IOException {
    importSimpleApplication();
    myTranslationsEditor.getTable().enterValue(TableCell.row(0).column(HEBREW_COLUMN), "app_name_main_iw");

    Object line = myGuiTest.ideFrame().getEditor()
      .open("app/src/main/res/values-iw/strings.xml")
      .moveBetween("app_name", "\"")
      .getCurrentLine()
      .trim();

    assertEquals("<string name=\"app_name\">app_name_main_iw</string>", line);
  }

  @Test
  public void rename() throws IOException {
    importSimpleApplication();

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
  public void resourceFolderColumn() throws IOException {
    importSimpleApplication();

    JTableFixture table = myTranslationsEditor.getTable();
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(0).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(1).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(2).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(3).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/main/res"), table.valueAt(TableCell.row(4).column(RESOURCE_FOLDER_COLUMN)));
    assertEquals(toResourceName("app/src/debug/res"), table.valueAt(TableCell.row(5).column(RESOURCE_FOLDER_COLUMN)));
  }

  @Test
  public void keySorting() throws IOException {
    importSimpleApplication();

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
  public void keyColumnWidthDoesntResetWhenAddingKey() throws IOException {
    importSimpleApplication();
    JTableFixture table = myTranslationsEditor.getTable();

    // A new width not equal to the current one
    int width = 127;

    GuiTask.execute(() -> {
      TableColumn column = table.target().getColumnModel().getColumn(KEY_COLUMN);

      assert width >= column.getMinWidth() && width != column.getPreferredWidth();
      column.setPreferredWidth(width);
    });

    myTranslationsEditor.getAddKeyButton().click();

    AddKeyDialogFixture dialog = myTranslationsEditor.getAddKeyDialog();
    dialog.getDefaultValueTextField().enterText("key_1");
    dialog.getKeyTextField().enterText("key_1");
    dialog.getOkButton().click();

    assertEquals(width, (long)GuiQuery.getNonNull(() -> table.target().getColumnModel().getColumn(KEY_COLUMN).getPreferredWidth()));
  }

  @Test
  public void invalidDefaultValueXml() throws IOException {
    WizardUtils.createNewProject(myGuiTest, "Empty Activity");
    Path stringsXml = FileSystems.getDefault().getPath("app", "src", "main", "res", "values", "strings.xml");

    GuiTestFileUtils.writeAndReloadDocument(
      myGuiTest.getProjectPath().toPath().resolve(stringsXml),

      "<resources>\n" +
      "    <string name=\"oslo_bysykkel_terms_url\">https://oslobysykkel.no/_app/options/terms?locale=%1$s&product_id=%2$s</string>\n" +
      "</resources>\n");

    openTranslationsEditor(stringsXml);

    SimpleColoredComponent component = myTranslationsEditor.getCellRenderer(1, 1);
    assert component != null;

    assertEquals("https://oslobysykkel.no/_app/options/terms?locale=%1$s&product_id=%2$s", component.myValue);
    assertEquals(SimpleTextAttributes.STYLE_WAVED, component.myAttributes.getStyle());
    assertEquals(JBColor.RED, component.myAttributes.getFgColor());
    assertEquals("Invalid XML", component.myTooltipText);
  }

  @Test
  public void invalidTranslationXml() throws IOException {
    WizardUtils.createNewProject(myGuiTest, "Empty Activity");
    Path stringsXml = FileSystems.getDefault().getPath("app", "src", "main", "res", "values-en", "strings.xml");

    GuiTestFileUtils.writeAndReloadDocument(
      myGuiTest.getProjectPath().toPath().resolve(stringsXml),

      "<resources>\n" +
      "    <string name=\"oslo_bysykkel_terms_url\">https://oslobysykkel.no/_app/options/terms?locale=%1$s&product_id=%2$s</string>\n" +
      "</resources>\n");

    openTranslationsEditor(stringsXml);

    SimpleColoredComponent component = myTranslationsEditor.getCellRenderer(0, 1);
    assert component != null;

    assertEquals("https://oslobysykkel.no/_app/options/terms?locale=%1$s&product_id=%2$s", component.myValue);
    assertEquals(SimpleTextAttributes.STYLE_WAVED, component.myAttributes.getStyle());
    assertEquals(JBColor.RED, component.myAttributes.getFgColor());
    assertEquals("Invalid XML", component.myTooltipText);
  }

  @Test
  public void selectedCellIsntLostAfterEnteringValue() throws IOException {
    importSimpleApplication();

    FixedColumnTableFixture table = myTranslationsEditor.getTable();
    TableCell cell = TableCell.row(0).column(3);
    table.enterValue(cell, "app_name");

    assertEquals(cell, table.selectedCell());
  }

  @Test
  public void enteringTextInDefaultValueTextFieldUpdatesTableCell() throws IOException {
    importSimpleApplication();

    JTableFixture table = myTranslationsEditor.getTable();
    TableCell actionSettingsDefaultValue = TableCell.row(2).column(DEFAULT_VALUE_COLUMN);
    table.selectCell(actionSettingsDefaultValue);

    JTextComponentFixture field = myTranslationsEditor.getDefaultValueTextField();
    field.selectAll();
    field.enterText("action_settings");

    TableCell appNameDefaultValue = TableCell.row(0).column(DEFAULT_VALUE_COLUMN);
    table.selectCell(appNameDefaultValue);

    myTranslationsEditor.waitUntilTableValueAtEquals(actionSettingsDefaultValue, "action_settings");
    assertEquals("Simple Application", table.valueAt(appNameDefaultValue));
  }

  @Test
  public void enteringTextInTranslationTextFieldUpdatesTableCell() throws IOException {
    importSimpleApplication();

    TableCell cancelEnglishTranslation = TableCell.row(3).column(ENGLISH_COLUMN);
    myTranslationsEditor.getTable().selectCell(cancelEnglishTranslation);

    JTextComponentFixture field = myTranslationsEditor.getTranslationTextField();
    field.selectAll();
    field.enterText("cancel_en");

    myTranslationsEditor.waitUntilTableValueAtEquals(cancelEnglishTranslation, "cancel_en");
  }

  @Test
  public void translationTextFieldFontCanDisplayPastedHebrew() throws IOException {
    importSimpleApplication();

    myTranslationsEditor.getTable().selectCell(TableCell.row(1).column(HEBREW_COLUMN));
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection("יישום פשוט"), EmptyClipboardOwner.INSTANCE);

    JTextComponentFixture translationTextField = myTranslationsEditor.getTranslationTextField();
    KeyStroke keyStroke = getKeyStroke(translationTextField.target().getInputMap(), DefaultEditorKit.pasteAction);
    translationTextField.pressAndReleaseKey(KeyPressInfo.keyCode(keyStroke.getKeyCode()).modifiers(keyStroke.getModifiers()));

    assertEquals(-1, translationTextField.font().target().canDisplayUpTo("יישום פשוט"));
  }

  @Test
  public void multilineEditUpdateShowsInTable() throws IOException {
    importSimpleApplication();
    myTranslationsEditor.getTable().selectCell(TableCell.row(1).column(ENGLISH_COLUMN));

    /* TODO Ideally, this would be good to have an option on the GuiTestRunner to avoid showing any notification for tests
     * because the notification prevent the robot to click on the multiline editor button */
    Notification notification =
      ServiceManager.getService(myGuiTest.ideFrame().getProject(), AndroidNotification.class).getNotification();
    if (notification != null) {
      notification.hideBalloon();
    }

    MultilineStringEditorDialogFixture editor = myTranslationsEditor.getMultilineEditorDialog();
    editor.getTranslationEditorTextField().replaceText("Multiline\nTest");
    editor.clickOk();
    myTranslationsEditor.getTable().cell(TableCell.row(1).column(ENGLISH_COLUMN)).requireValue("Multiline\nTest");
  }

  @Test
  public void deleteString() throws IOException {
    importSimpleApplication();

    // delete just the translation
    myTranslationsEditor.getTable().selectCell(TableCell.row(1).column(ENGLISH_COLUMN));
    myGuiTest.robot().pressAndReleaseKey(KeyEvent.VK_DELETE);

    // gone from en
    EditorFixture editor = myGuiTest.ideFrame().getEditor().open("app/src/main/res/values-en/strings.xml");
    assertThat(editor.getCurrentFileContents()).doesNotContain("hello_world");

    // still in other languages
    editor = myGuiTest.ideFrame().getEditor().open("app/src/main/res/values-ta/strings.xml");
    assertThat(editor.getCurrentFileContents()).contains("hello_world");
  }

  @Test
  public void deleteKey() throws IOException {
    importSimpleApplication();

    // delete the entire string
    myTranslationsEditor.getTable().selectCell(TableCell.row(1).column(KEY_COLUMN));
    myGuiTest.robot().pressAndReleaseKey(KeyEvent.VK_DELETE);
    DeleteDialogFixture.find(myGuiTest.robot(), "Delete").safe(false).clickOk().waitUntilNotShowing();

    // gone from all languages
    EditorFixture editor = myGuiTest.ideFrame().getEditor().open("app/src/main/res/values/strings.xml");
    assertThat(editor.getCurrentFileContents()).doesNotContain("hello_world");
    editor = myGuiTest.ideFrame().getEditor().open("app/src/main/res/values-ta/strings.xml");
    assertThat(editor.getCurrentFileContents()).doesNotContain("hello_world");
  }

  @Test
  public void deleteRightClick() throws IOException {
    importSimpleApplication();

    myTranslationsEditor.getTable().showPopupMenuAt(TableCell.row(1).column(ENGLISH_COLUMN)).menuItemWithPath("Delete String(s)").click();
    EditorFixture editor = myGuiTest.ideFrame().getEditor().open("app/src/main/res/values-en/strings.xml");
    assertThat(editor.getCurrentFileContents()).doesNotContain("hello_world");
  }

  @Test
  public void deleteSafe() throws IOException {
    importSimpleApplication();

    // delete the entire string
    myTranslationsEditor.getTable().selectCell(TableCell.row(1).column(KEY_COLUMN));
    myGuiTest.robot().pressAndReleaseKey(KeyEvent.VK_DELETE);
    DeleteDialogFixture.find(myGuiTest.robot(), "Delete").safe(true).clickOk().waitForUnsafeDialog().deleteAnyway();

    // gone from all languages
    EditorFixture editor = myGuiTest.ideFrame().getEditor().open("app/src/main/res/values/strings.xml");
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
