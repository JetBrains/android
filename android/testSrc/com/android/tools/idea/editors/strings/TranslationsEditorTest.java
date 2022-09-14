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

import static com.android.tools.idea.concurrency.AsyncTestUtils.waitForCondition;
import static com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents;
import static org.junit.Assert.assertEquals;

import com.android.ide.common.resources.Locale;
import com.android.tools.idea.editors.strings.model.StringResourceKey;
import com.android.tools.idea.editors.strings.table.FrozenColumnTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.editors.strings.table.StringTableCellEditor;
import com.android.tools.idea.io.TestFileUtils;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.StringResourceWriter;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import org.intellij.lang.annotations.Language;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@RunsInEdt
public final class TranslationsEditorTest {
  private final AndroidProjectRule projectRule = AndroidProjectRule.onDisk();
  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(projectRule).around(new EdtRule());

  private Path myRes;
  private StringResourceViewPanel myPanel;
  private FrozenColumnTable<?> myTable;

  @Before
  public void setUp() throws Exception {
    myRes = Paths.get(projectRule.getProject().getBasePath(), "app/src/main/res");

    @Language("XML")
    String contents = "<resources>\n" +
                      "    <string name=\"key_1\">key_1_default</string>\n" +
                      "</resources>\n";

    TestFileUtils.writeFileAndRefreshVfs(myRes.resolve(Paths.get("values", "strings.xml")), contents);

    Module module = projectRule.getModule();
    StringResourceEditor editor = new StringResourceEditor(StringsVirtualFile.getStringsVirtualFile(module));
    Disposer.register(projectRule.getFixture().getTestRootDisposable(), editor);
    myPanel = editor.getPanel();
    myTable = myPanel.getTable();
  }

  @Test
  public void reusedColumnHeaderValuesAreCleared() {
    Utils.loadResources(myPanel, Collections.singletonList(myRes));

    StringResourceKey key = myPanel.getTable().getData().getKeys().stream().filter(k -> k.getDirectory() != null).findFirst().orElseThrow();

    StringResourceWriter.INSTANCE.addTranslation(projectRule.getProject(), key, "", Locale.create("b+ace"),
                                                 IdeResourcesUtil.DEFAULT_STRING_RESOURCE_FILE_NAME, null);

    Utils.loadResources(myPanel, Collections.singletonList(myRes));

    StringResourceWriter.INSTANCE.addTranslation(projectRule.getProject(), key, "", Locale.create("ab"),
                                                 IdeResourcesUtil.DEFAULT_STRING_RESOURCE_FILE_NAME, null);

    Utils.loadResources(myPanel, Collections.singletonList(myRes));

    TableColumnModel model = myTable.getScrollableTable().getColumnModel();

    Object values = IntStream.range(0, model.getColumnCount())
      .mapToObj(model::getColumn)
      .map(TableColumn::getHeaderValue)
      .collect(Collectors.toList());

    assertEquals(Arrays.asList("Abkhazian (ab)", "Achinese (ace)"), values);
  }

  @Test
  public void setKeyName() throws Exception {
    Utils.loadResources(myPanel, Collections.singletonList(myRes));
    myTable.editCellAt(0, StringResourceTableModel.KEY_COLUMN);

    StringTableCellEditor cellEditor = (StringTableCellEditor)myTable.getCellEditor();
    cellEditor.setCellEditorValue("key_2");
    cellEditor.stopCellEditing();

    dispatchAllInvocationEvents();
    assertEquals("key_2", myTable.getValueAt(0, StringResourceTableModel.KEY_COLUMN));

    myTable.editCellAt(0, StringResourceTableModel.DEFAULT_VALUE_COLUMN);

    cellEditor.setCellEditorValue("key_2_default");
    cellEditor.stopCellEditing();

    // Updates of the default value column are asynchronous. Wait for the update to complete.
    waitForCondition(2, TimeUnit.SECONDS, () -> !myTable.getValueAt(0, StringResourceTableModel.DEFAULT_VALUE_COLUMN).equals(""));
    assertEquals("key_2_default", myTable.getValueAt(0, StringResourceTableModel.DEFAULT_VALUE_COLUMN));
  }
}
