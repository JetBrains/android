/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.hierarchyview.model;

import com.android.tools.idea.editors.hierarchyview.ui.ViewNodeTableModel;
import org.junit.Test;

import static org.junit.Assert.*;

public class ViewNodeTest {

  @Test
  public void testParseNodeTree() throws Exception {
    ViewNode root = ViewNode.parseFlatString(getViewNodeFlatString());
    assertEquals(root.id, "god");
    assertEquals(root.getChildCount(), 2);
    assertEquals(root.getChildAt(1).getChildCount(), 1);
  }

  @Test
  public void testParseNodeAttrs() throws Exception {
    ViewNode node = ViewNode.parseFlatString(getViewNodeFlatString()).getChildAt(0);
    assertTrue(node.getProperty("cat:foo").getValue().endsWith("happy"));
    assertEquals(node.getProperty("cow:child").getValue(), "calf");

    node = ViewNode.parseFlatString(getViewNodeFlatString()).getChildAt(1).getChildAt(0);
    assertEquals(node.getProperty("cat:foo").getValue(), "this is a long text");
  }

  @Test
  public void testPropertiesOrdering() throws Exception {
    ViewNode root = ViewNode.parseFlatString(getViewNodeFlatString());

    ViewProperty prop1 = root.getChildAt(0).getProperty("cat:foo");
    ViewProperty prop2 = root.getChildAt(1).getChildAt(0).getProperty("cat:foo");

    assertEquals(prop1, prop2);
    assertTrue(prop1.compareTo(prop2) == 0);

    // Test comparison between properties with null and non-null category values
    ViewProperty prop3 = root.getProperty("zoo");
    assertEquals(prop3.compareTo(prop1), -1);
    assertEquals(prop1.compareTo(prop3), 1);
    assertNotEquals(prop1, prop3);

    // Test same non-null category values
    ViewProperty prop4 = root.getChildAt(0).getProperty("cow:foo");
    ViewProperty prop5 = root.getChildAt(0).getProperty("cow:child");
    assertNotEquals(prop4, prop5);
    assertEquals(prop4.compareTo(prop5), 1);
    assertEquals(prop5.compareTo(prop4), -1);

    // Test non-null categories ordering with the same property name
    assertNotEquals(prop1, prop4);
    assertEquals(prop1.compareTo(prop4), -1);
    assertEquals(prop4.compareTo(prop1), 1);
  }

  @Test
  public void testViewNodeTableModel() throws Exception {
    ViewNode node = ViewNode.parseFlatString(getViewNodeFlatString());
    ViewNodeTableModel model = new ViewNodeTableModel();
    model.setNode(node);
    assertEquals(4, model.getRowCount());
    // Arbitrarily take the second row for testing
    assertTrue(model.getValueAt(1, 0) instanceof String);
    assertTrue(model.getValueAt(1, 1) instanceof String);
    assertEquals("zoo", model.getValueAt(1, 0));
    assertEquals("baz", model.getValueAt(1, 1));
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
}
