/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.editors.layoutInspector.LayoutInspectorContext;
import com.android.tools.idea.editors.layoutInspector.model.ViewNode;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.table.JBTable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.swing.table.TableRowSorter;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PropertiesTablePanelTest {
  private PropertiesTablePanel myPanel;

  @Before
  public void setUp() {
    ViewNode node = ViewNode.parseFlatString(getViewNodeFlatString());
    ViewNodeTableModel model = new ViewNodeTableModel();
    model.setNode(node);
    JBTable table = new JBTable(model);

    myPanel = new PropertiesTablePanel();
    LayoutInspectorContext context = mock(LayoutInspectorContext.class);
    when(context.getPropertiesTable()).thenReturn(table);
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
    JBTable table = myPanel.getTable();
    TableRowSorter<ViewNodeTableModel> sorter = (TableRowSorter<ViewNodeTableModel>)table.getRowSorter();
    assertThat(sorter.getRowFilter()).isNull();
    assertThat(table.getRowCount()).isEqualTo(4);

    myPanel.setFilter("m");
    assertThat(sorter.getRowFilter()).isNotNull();
    assertThat(table.getRowCount()).isEqualTo(1);
    assertThat(table.getModel().getValueAt(table.convertRowIndexToModel(0), 0)).isEqualTo("mID");
  }
}
