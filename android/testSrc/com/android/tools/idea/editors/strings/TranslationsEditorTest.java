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

import com.android.tools.idea.editors.strings.table.FrozenColumnTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.editors.strings.table.StringTableCellEditor;
import com.android.tools.idea.io.TestFileUtils;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import org.intellij.lang.annotations.Language;
import org.junit.Rule;
import org.junit.Test;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public final class TranslationsEditorTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  @Test
  public void setKeyName() throws Exception {
    FileSystem fileSystem = FileSystems.getDefault();
    Module module = myRule.getModule();
    Path res = fileSystem.getPath(module.getProject().getBasePath(), "app", "src", "main", "res");

    @Language("XML")
    String contents = "<resources>\n" +
                      "    <string name=\"key_1\">key_1_default</string>\n" +
                      "</resources>\n";

    TestFileUtils.writeFileAndRefreshVfs(fileSystem.getPath(res.toString(), "values", "strings.xml"), contents);
    StringResourceEditor translationsEditor = new StringResourceEditor(StringsVirtualFile.getStringsVirtualFile(module));

    try {
      Application application = ApplicationManager.getApplication();
      StringResourceViewPanel panel = translationsEditor.getPanel();
      FrozenColumnTable table = panel.getTable();

      application.invokeAndWait(() -> {
        Utils.loadResources(panel, res);
        table.editCellAt(0, StringResourceTableModel.KEY_COLUMN);

        StringTableCellEditor cellEditor = (StringTableCellEditor)table.getCellEditor();
        cellEditor.setCellEditorValue("key_2");
        cellEditor.stopCellEditing();
      });

      application.invokeAndWait(() -> assertEquals("key_2", table.getValueAt(0, StringResourceTableModel.KEY_COLUMN)));

      application.invokeAndWait(() -> {
        table.editCellAt(0, StringResourceTableModel.DEFAULT_VALUE_COLUMN);

        StringTableCellEditor cellEditor = (StringTableCellEditor)table.getCellEditor();
        cellEditor.setCellEditorValue("key_2_default");
        cellEditor.stopCellEditing();
      });

      application.invokeAndWait(() -> assertEquals("key_2_default", table.getValueAt(0, StringResourceTableModel.DEFAULT_VALUE_COLUMN)));
    }
    finally {
      Disposer.dispose(translationsEditor);
    }
  }
}
