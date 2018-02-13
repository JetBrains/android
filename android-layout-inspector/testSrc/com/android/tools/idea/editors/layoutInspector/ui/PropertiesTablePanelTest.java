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
package com.android.tools.idea.editors.layoutInspector.ui;

import com.android.layoutinspector.model.ViewNode;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import com.android.tools.idea.editors.layoutInspector.LayoutInspectorContext;
import com.android.tools.idea.editors.layoutInspector.ptable.LITableRendererProvider;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.AndroidTestCase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.*;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PropertiesTablePanelTest {
  private PropertiesTablePanel myPanel;

  @Before
  public void setUp() {
    ViewNode node = ViewNode.parseFlatString(getViewNodeFlatString());
    PTableModel model = new PTableModel();
    model.setItems(LayoutInspectorContext.convertToItems(node.getGroupedProperties()));
    CopyPasteManager mockManager = mock(CopyPasteManager.class);
    PTable table = new PTable(model, mockManager);
    table.setRendererProvider(LITableRendererProvider.getInstance());
    myPanel = new PropertiesTablePanel();
    LayoutInspectorContext context = mock(LayoutInspectorContext.class);
    when(context.getPropertiesTable()).thenReturn(table);
    when(context.getTableModel()).thenReturn(model);
    myPanel.setToolContext(context);
  }

  private static byte[] getViewNodeFlatString() {
    String text =
      "myroot@191 cat:foo=4,4394 cat2:foo2=5,hello zoo=3,baz mID=3,god \n" +
      "  node1@3232 cat:foo=8,[] happy cow:child=4,calf cow:foo=5,super mID=9,not-a-god \n" +
      "  node2@222 noun:eg=10,alpha beta mID=11,maybe-a-god \n" +
      "    node3@3333 mID=11,another-god cat:foo=19,this is a long text \n" +
      "DONE.\n";
    return text.getBytes();
  }

  @After
  public void tearDown() {
    Disposer.dispose(myPanel);
  }

  @Test
  public void testSetFilter() {
    PTable table = myPanel.getTable();
    assertThat(table.getRowCount()).isEqualTo(3);

    myPanel.setFilter("c");
    RowSorter sorter = table.getRowSorter();
    assertThat(sorter).isNotNull();
    assertThat(table.getRowCount()).isEqualTo(2);
    assertThat(((PTableItem) table.getModel().getValueAt(table.convertRowIndexToModel(0), 0)).getName()).isEqualTo("cat");
  }
}
