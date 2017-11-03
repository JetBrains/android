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
package com.android.tools.adtui.ptable;

import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TableModelListener;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

public class PTableModelTest extends TestCase {
  private PTableModel myModel;
  private TableModelListener myListener;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myListener = mock(TableModelListener.class);
    myModel = new PTableModel();
    myModel.addTableModelListener(myListener);
  }

  public void testBasics() {
    List<PTableItem> items = createItems(
      "item1", null,
      "item2", null,
      "item3", null
    );
    myModel.setItems(items);
    verify(myListener).tableChanged(any());

    assertEquals(3, myModel.getRowCount());

    assertEquals("item1", ((SimpleItem)myModel.getValueAt(0, 0)).getName());
  }

  public void testExpandCollapse() {
    List<PTableItem> items = createItems(
      "item1", null,
      "item2", null,
      "item3", new Object[]{"child1", null, "child2", null});
    myModel.setItems(items);
    verify(myListener).tableChanged(any());

    // last node should be collapsed
    assertEquals(3, myModel.getRowCount());

    Object value = myModel.getValueAt(2, 0);
    assert value instanceof PTableItem;

    // expand and check that 2 more nodes have been added
    myModel.expand(2);
    assertEquals(5, myModel.getRowCount());
    verify(myListener, times(2)).tableChanged(any());

    myModel.collapse(2);
    assertEquals(3, myModel.getRowCount());
    verify(myListener, times(3)).tableChanged(any());
  }

  public void testGetParent() {
    List<PTableItem> items = createItems(
      "item1", new Object[]{"child1", null, "child2", null},
      "item2", new Object[]{"child1", null},
      "item3", new Object[]{"child1", null, "child2", null});
    myModel.setItems(items);
    myModel.expand(2);
    myModel.expand(0);

    assertThat(myModel.getParent(0)).isEqualTo(0);
    assertThat(myModel.getParent(1)).isEqualTo(0);
    assertThat(myModel.getParent(2)).isEqualTo(0);
    assertThat(myModel.getParent(3)).isEqualTo(3);
    assertThat(myModel.getParent(4)).isEqualTo(4);
    assertThat(myModel.getParent(5)).isEqualTo(4);
    assertThat(myModel.getParent(6)).isEqualTo(4);

    try {
      myModel.getParent(-1);
      fail();
    }
    catch (Exception ex) {
      assertThat(ex).isInstanceOf(ArrayIndexOutOfBoundsException.class);
    }
    assertThat(myModel.getParent(999)).isEqualTo(999);
  }

  public void testInsertRow() {
    List<PTableItem> items = createItems(
      "item1", new Object[]{"child1", null, "child2", null},
      "item2", new Object[]{"child1", null},
      "item3", new Object[]{"child1", null, "child2", null});
    myModel.setItems(new ArrayList<>(items));
    verify(myListener).tableChanged(any());

    SimpleItem extra = new SimpleItem("extra");
    myModel.insertRow(2, extra);
    assertThat(myModel.getRowCount()).isEqualTo(4);
    assertThat(myModel.getValueAt(1, 0)).isSameAs(items.get(1));
    assertThat(myModel.getValueAt(2, 0)).isSameAs(extra);
    assertThat(myModel.getValueAt(3, 0)).isSameAs(items.get(2));
    verify(myListener, times(2)).tableChanged(any());
  }

  public void testDeleteRow() {
    List<PTableItem> items = createItems(
      "item1", new Object[]{"child1", null, "child2", null},
      "item2", new Object[]{"child1", null},
      "item3", new Object[]{"child1", null, "child2", null});
    myModel.setItems(new ArrayList<>(items));
    verify(myListener).tableChanged(any());

    myModel.deleteRow(1);
    assertThat(myModel.getRowCount()).isEqualTo(2);
    assertThat(myModel.getValueAt(0, 0)).isSameAs(items.get(0));
    assertThat(myModel.getValueAt(1, 0)).isSameAs(items.get(2));
    verify(myListener, times(2)).tableChanged(any());
  }

  public void testRestoreExpansionAfterUpdatingItems() {
    List<PTableItem> items = createItems(
      "item1", new Object[]{"child1", null, "child2", null},
      "item2", new Object[]{"child1", null},
      "item3", new Object[]{"child1", null, "child2", null});
    myModel.setItems(items);
    myModel.expand(2);
    myModel.expand(0);

    List<PTableItem> newItems = createItems(
      "item0", new Object[]{"child1", null, "child2", null},
      "item1", new Object[]{"child1", null, "child2", null},
      "item2", new Object[]{"child1", null},
      "item3", new Object[]{"child1", null, "child2", null},
      "item4", new Object[]{"child1", null, "child2", null});
    myModel.setItems(new ArrayList<>(newItems));
    assertThat(myModel.getRowCount()).isEqualTo(9);
    assertThat(myModel.getValueAt(0, 0)).isSameAs(newItems.get(0));
    assertThat(myModel.getValueAt(1, 0)).isSameAs(newItems.get(1));
    assertThat(myModel.getValueAt(2, 0)).isSameAs(newItems.get(1).getChildren().get(0));
    assertThat(myModel.getValueAt(3, 0)).isSameAs(newItems.get(1).getChildren().get(1));
    assertThat(myModel.getValueAt(4, 0)).isSameAs(newItems.get(2));
    assertThat(myModel.getValueAt(5, 0)).isSameAs(newItems.get(3));
    assertThat(myModel.getValueAt(6, 0)).isSameAs(newItems.get(3).getChildren().get(0));
    assertThat(myModel.getValueAt(7, 0)).isSameAs(newItems.get(3).getChildren().get(1));
    assertThat(myModel.getValueAt(8, 0)).isSameAs(newItems.get(4));
  }

  private static List<PTableItem> createItems(Object... items) {
    assert (items.length % 2) == 0;

    List<PTableItem> result = Lists.newArrayList();

    for (int i = 0; i < items.length; i += 2) {
      SimpleItem node = new SimpleItem((String)items[i]);
      if (items[i + 1] != null) {
        node.setChildren(createItems((Object[])items[i + 1]));
      }
      result.add(node);
    }

    return result;
  }

  private static class SimpleItem extends PTableGroupItem {
    private final String myName;

    public SimpleItem(@NotNull String name) {
      myName = name;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }
  }
}
