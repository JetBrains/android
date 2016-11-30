/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.ptable;

import com.google.common.collect.Lists;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PTableModelTest extends TestCase {
  private PTableModel myModel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myModel = new PTableModel();
  }

  public void testBasics() {
    List<PTableItem> items = createItems(
      "item1", null,
      "item2", null,
      "item3", null
    );
    myModel.setItems(items);

    assertEquals(3, myModel.getRowCount());

    assertEquals("item1", ((SimpleItem)myModel.getValueAt(0, 0)).getName());
  }

  public void testExpandCollapse() {
    List<PTableItem> items = createItems(
      "item1", null,
      "item2", null,
      "item3", new String[]{"child1", null, "child2", null});
    myModel.setItems(items);

    // last node should be collapsed
    assertEquals(3, myModel.getRowCount());

    Object value = myModel.getValueAt(2, 0);
    assert value instanceof PTableItem;

    // expand and check that 2 more nodes have been added
    myModel.expand(2);
    assertEquals(5, myModel.getRowCount());

    myModel.collapse(2);
    assertEquals(3, myModel.getRowCount());
  }


  private static List<PTableItem> createItems(Object... items) {
    assert (items.length % 2) == 0;

    ArrayList<PTableItem> result = Lists.newArrayList();

    for (int i = 0; i < items.length; i += 2) {
      SimpleItem node = new SimpleItem((String)items[i]);
      if (items[i + 1] != null) {
        node.setChildren(createItems((String[]) items[i+1]));
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
