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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

  private static byte[] getViewNodeFlatString() {
    String text =
      "myroot@191 cat:foo=4,4394 cat2:foo2=5,hello mID=3,god \n" +
      "  node1@3232 cat:foo=8,\uD83D\uDE00 happy cow:child=4,calf mID=9,not-a-god \n" +
      "  node2@222 noun:eg=10,alpha beta mID=11,maybe-a-god \n" +
      "    node3@3333 mID=11,another-god cat:foo=19,this is a long text \n" +
      "DONE.\n";
    return text.getBytes();
  }
}
