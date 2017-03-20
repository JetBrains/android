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
package com.android.tools.idea.apk.viewer.dex.tree;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import javax.swing.*;
import java.io.IOException;
import java.util.Comparator;

import static org.junit.Assert.assertEquals;

public class DexElementNodeTest {

  private static class ConcreteNode extends DexElementNode {
    ConcreteNode(@NotNull String name, boolean allowsChildren) {
      super(name, allowsChildren);
    }

    @Override
    public Icon getIcon() {
      return null;
    }
  }

  private static class AnotherNode extends DexElementNode {
    AnotherNode(@NotNull String name, boolean allowsChildren) {
      super(name, allowsChildren);
    }

    @Override
    public Icon getIcon() {
      return null;
    }
  }

  @Test
  public void getChildByTypeTest() throws IOException {
    DexElementNode node = new DexElementNode("root", true) {
      @Override
      public Icon getIcon() {
        return null;
      }
    };
    ConcreteNode childNode = new ConcreteNode("name_1", false);
    ConcreteNode childNode2 = new ConcreteNode("name_2", false);

    AnotherNode childNode3 = new AnotherNode("name_1", false);
    AnotherNode childNode4 = new AnotherNode("name_other", false);

    node.add(childNode);
    node.add(childNode2);
    node.add(childNode3);
    node.add(childNode4);

    assertEquals(childNode, node.getChildByType("name_1", ConcreteNode.class));
    assertEquals(childNode2, node.getChildByType("name_2", ConcreteNode.class));
    assertEquals(childNode3, node.getChildByType("name_1", AnotherNode.class));
    assertEquals(childNode4, node.getChildByType("name_other", AnotherNode.class));

    assertEquals("root", node.getName());
  }


  @Test
  public void sortTest() throws IOException {
    DexElementNode node = new DexElementNode("root", true) {
      @Override
      public Icon getIcon() {
        return null;
      }
    };
    ConcreteNode childNode = new ConcreteNode("name_2", false);
    ConcreteNode childNode2 = new ConcreteNode("name_1", false);

    AnotherNode childNode3 = new AnotherNode("name_4", false);
    AnotherNode childNode4 = new AnotherNode("name_3", false);

    node.add(childNode);
    node.add(childNode2);
    node.add(childNode3);
    node.add(childNode4);
    node.sort(Comparator.comparing(DexElementNode::getName));

    assertEquals(childNode2, node.getChildAt(0));
    assertEquals(childNode, node.getChildAt(1));
    assertEquals(childNode4, node.getChildAt(2));
    assertEquals(childNode3, node.getChildAt(3));
  }


}

