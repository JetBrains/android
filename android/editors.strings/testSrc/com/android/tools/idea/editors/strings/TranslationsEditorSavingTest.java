/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertTrue;

import com.android.tools.idea.editors.strings.table.FrozenColumnTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.editors.strings.table.StringTableCellEditor;
import com.android.tools.idea.io.TestFileUtils;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.intellij.lang.annotations.Language;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TranslationsEditorSavingTest {
  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  private Application myApplication;
  private Path myRes;
  private StringResourceEditor myEditor;
  private StringResourceViewPanel myPanel;
  private FrozenColumnTable myTable;
  private final List<Path> myFilesToRemove = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    myApplication = ApplicationManager.getApplication();

    Module module = myRule.getModule();
    myRes = Paths.get(module.getProject().getBasePath(), "app", "src", "main", "res");

    @Language("XML")
    String contents = "<resources>\n" +
                      "    <string name=\"key_1\">key_1_default</string>\n" +
                      "    <string name=\"key_2\">key_2_default</string>\n" +
                      "    <string name=\"key_3\">key_3_default</string>\n" +
                      "</resources>\n";
    Path path = myRes.resolve(Paths.get("values", "strings.xml"));
    myFilesToRemove.add(path);
    TestFileUtils.writeFileAndRefreshVfs(path, contents);

    @Language("XML")
    String translatedContents = "<resources>\n" +
                                "    <string name=\"key_1\">key_1_translated</string>\n" +
                                "</resources>\n";
    path = myRes.resolve(Paths.get("values-zh-v11", "strings.xml"));
    myFilesToRemove.add(path);
    TestFileUtils.writeFileAndRefreshVfs(path, translatedContents);

    myEditor = new StringResourceEditor(StringsVirtualFile.getStringsVirtualFile(module));
    myPanel = myEditor.getPanel();
    myTable = myPanel.getTable();
  }

  @After
  public void tearDown() throws Exception {
    for (Path path : myFilesToRemove) {
      Files.delete(path);
    }
    LocalFileSystem.getInstance().refresh(false);
    Disposer.dispose(myEditor);
  }

  @Test
  public void saveNewTranslationSingleSource() {
    myApplication.invokeAndWait(() -> {
      Utils.loadResources(myPanel, Collections.singletonList(myRes));

      myTable.editCellAt(1, StringResourceTableModel.DEFAULT_VALUE_COLUMN + 1);
      StringTableCellEditor cellEditor = (StringTableCellEditor)myTable.getCellEditor();
      cellEditor.setCellEditorValue("key_2_translated");
      cellEditor.stopCellEditing();
    });

    VirtualFile vFile =
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myRes.resolve(Paths.get("values-zh", "strings.xml")).toFile());
    assertTrue(vFile == null || !vFile.exists());
  }

  @Test
  public void saveNewTranslationMultipleSources() throws Exception {
    @Language("XML")
    String translatedContents = "<resources>\n" +
                                "    <string name=\"key_2\">key_2_translated</string>\n" +
                                "</resources>\n";
    Path v12Path = myRes.resolve(Paths.get("values-zh-v12", "strings.xml"));
    TestFileUtils.writeFileAndRefreshVfs(v12Path, translatedContents);
    myFilesToRemove.add(v12Path);

    myApplication.invokeAndWait(() -> {
      Utils.loadResources(myPanel, Collections.singletonList(myRes));

      myTable.editCellAt(2, StringResourceTableModel.DEFAULT_VALUE_COLUMN + 1);
      StringTableCellEditor cellEditor = (StringTableCellEditor)myTable.getCellEditor();
      cellEditor.setCellEditorValue("key_3_translated");
      cellEditor.stopCellEditing();
    });

    VirtualFile vFile =
      LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myRes.resolve(Paths.get("values-zh", "strings.xml")).toFile());
    assertTrue(vFile != null && vFile.exists());
    myFilesToRemove.add(vFile.toNioPath());
  }
}
