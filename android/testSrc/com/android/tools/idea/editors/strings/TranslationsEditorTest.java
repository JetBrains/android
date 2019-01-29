/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.editors.strings.table.FrozenColumnTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.editors.strings.table.StringTableCellEditor;
import com.android.tools.idea.io.TestFileUtils;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.intellij.lang.annotations.Language;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class TranslationsEditorTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private Application myApplication;
  private Path myRes;
  private StringResourceEditor myEditor;
  private StringResourceViewPanel myPanel;
  private FrozenColumnTable myTable;

  @Before
  public void setUp() throws Exception {
    myApplication = ApplicationManager.getApplication();

    Module module = myRule.getModule();
    myRes = Paths.get(module.getProject().getBasePath(), "app", "src", "main", "res");

    @Language("XML")
    String contents = "<resources>\n" +
                      "    <string name=\"key_1\">key_1_default</string>\n" +
                      "</resources>\n";

    TestFileUtils.writeFileAndRefreshVfs(myRes.resolve(Paths.get("values", "strings.xml")), contents);

    myEditor = new StringResourceEditor(StringsVirtualFile.getStringsVirtualFile(module));
    myPanel = myEditor.getPanel();
    myTable = myPanel.getTable();
  }

  @After
  public void tearDown() {
    Disposer.dispose(myEditor);
  }

  @Test
  public void reusedColumnHeaderValuesAreCleared() {
    myApplication.invokeAndWait(() -> {
      Utils.loadResources(myPanel, myRes);

      AddLocaleAction action = myPanel.getAddLocaleAction();
      action.createItem(Locale.create("b+ace"));
      Utils.loadResources(myPanel, myRes);

      action.createItem(Locale.create("ab"));
      Utils.loadResources(myPanel, myRes);
    });

    TableColumnModel model = myTable.getScrollableTable().getColumnModel();

    Object values = IntStream.range(0, model.getColumnCount())
                             .mapToObj(model::getColumn)
                             .map(TableColumn::getHeaderValue)
                             .collect(Collectors.toList());

    assertEquals(Arrays.asList("Abkhazian (ab)", "Achinese (ace)"), values);
  }

  @Test
  public void setKeyName() {
    myApplication.invokeAndWait(() -> {
      Utils.loadResources(myPanel, myRes);
      myTable.editCellAt(0, StringResourceTableModel.KEY_COLUMN);

      StringTableCellEditor cellEditor = (StringTableCellEditor)myTable.getCellEditor();
      cellEditor.setCellEditorValue("key_2");
      cellEditor.stopCellEditing();
    });

    myApplication.invokeAndWait(() -> assertEquals("key_2", myTable.getValueAt(0, StringResourceTableModel.KEY_COLUMN)));

    myApplication.invokeAndWait(() -> {
      myTable.editCellAt(0, StringResourceTableModel.DEFAULT_VALUE_COLUMN);

      StringTableCellEditor cellEditor = (StringTableCellEditor)myTable.getCellEditor();
      cellEditor.setCellEditorValue("key_2_default");
      cellEditor.stopCellEditing();
    });

    myApplication.invokeAndWait(() -> assertEquals("key_2_default", myTable.getValueAt(0, StringResourceTableModel.DEFAULT_VALUE_COLUMN)));
  }
}
