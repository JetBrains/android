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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.TranslationsEditorFixture;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.io.IOException;
import java.util.Arrays;

import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab.EDITOR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    Object expected = Arrays.asList("English (en)", "English (en) in United Kingdom (GB)", "Tamil (ta)", "Chinese (zh) in China (CN)");
    assertEquals(expected, myTranslationsEditor.locales());

    assertEquals(Arrays.asList("action_settings", "app_name", "cancel", "hello_world"), myTranslationsEditor.keys());

    JTableCellFixture cancel = myTranslationsEditor.getTable().cell(TableCell.row(2).column(6)); // Cancel in zh-rCN
    assertEquals("取消", cancel.value());
    assertTrue(GuiQuery.getNonNull(() -> cancel.font().target().canDisplay('消')));
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
}
