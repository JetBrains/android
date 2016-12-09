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
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.TranslationsEditorFixture;
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

import static com.android.tools.idea.editors.strings.table.StringResourceTableModel.DEFAULT_VALUE_COLUMN;
import static com.android.tools.idea.editors.strings.table.StringResourceTableModel.KEY_COLUMN;
import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab.EDITOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(GuiTestRunner.class)
public final class TranslationsEditorTest {
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

    assertEquals(Arrays.asList("action_settings", "app_name", "cancel", "hello_world"), myTranslationsEditor.keys());

    JTableCellFixture cancel = myTranslationsEditor.getTable().cell(TableCell.row(2).column(7)); // Cancel in zh-rCN
    assertEquals("取消", cancel.value());
    assertEquals(-1, cancel.font().target().canDisplayUpTo("取消"));
  }

  @Test
  public void showKeysNeedingTranslationForEnglish() {
    myTranslationsEditor.clickFilterKeysComboBoxItem("Show Keys Needing a Translation for English (en)");
    assertEquals(Collections.singletonList("cancel"), myTranslationsEditor.keys());
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
    table.selectCell(TableCell.row(1).column(2));

    String data = "app_name\tapp_name_en\n" +
                  "cancel\tcancel_en\n";

    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(data), EmptyClipboardOwner.INSTANCE);

    KeyStroke keyStroke = getKeyStroke(table.target().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT), "paste");
    table.pressAndReleaseKey(KeyPressInfo.keyCode(keyStroke.getKeyCode()).modifiers(keyStroke.getModifiers()));

    assertEquals("app_name", table.valueAt(TableCell.row(1).column(2)));
    assertEquals("app_name_en", table.valueAt(TableCell.row(1).column(3)));
    assertEquals("cancel", table.valueAt(TableCell.row(2).column(2)));
    assertEquals("cancel_en", table.valueAt(TableCell.row(2).column(3)));
  }

  @Test
  public void enteringTextInTranslationTextFieldUpdatesTableCell() {
    JTableFixture table = myTranslationsEditor.getTable();
    TableCell cell = TableCell.row(2).column(3);

    table.selectCell(cell);
    myTranslationsEditor.getTranslationTextField().enterText("cancel_en");

    // Make the Translation text field lose focus
    myTranslationsEditor.getKeyTextField().focus();

    assertEquals("cancel_en", table.valueAt(cell));
  }

  @Test
  public void translationTextFieldFontCanDisplayPastedHebrew() {
    myTranslationsEditor.getTable().selectCell(TableCell.row(1).column(5));
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection("יישום פשוט"), EmptyClipboardOwner.INSTANCE);

    JTextComponentFixture translationTextField = myTranslationsEditor.getTranslationTextField();
    KeyStroke keyStroke = getKeyStroke(translationTextField.target().getInputMap(), DefaultEditorKit.pasteAction);
    translationTextField.pressAndReleaseKey(KeyPressInfo.keyCode(keyStroke.getKeyCode()).modifiers(keyStroke.getModifiers()));

    assertEquals(-1, translationTextField.font().target().canDisplayUpTo("יישום פשוט"));
  }

  @NotNull
  private static KeyStroke getKeyStroke(@NotNull InputMap inputMap, @NotNull Object actionMapKey) {
    Optional<KeyStroke> optionalKeyStroke = Arrays.stream(inputMap.allKeys())
      .filter(keyStroke -> inputMap.get(keyStroke).equals(actionMapKey))
      .findFirst();

    return optionalKeyStroke.orElseThrow(() -> new IllegalArgumentException(actionMapKey.toString()));
  }
}
