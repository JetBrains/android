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
package com.android.tools.idea.navigator;

import com.intellij.testFramework.IdeaTestCase;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import static com.android.tools.idea.navigator.AndroidProjectViewPane.SELECTED_TREE_NODES;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link AndroidProjectViewPane}.
 */
public class AndroidProjectViewPaneTest extends IdeaTestCase {
  private AndroidProjectViewPane myPane;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myPane = new AndroidProjectViewPane(getProject());
    myPane.createComponent();
  }

  public void testGetDataWithSelectedTreeNodesKey() throws Exception {
    JTree tree = myPane.getTree();
    DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode)tree.getModel().getRoot();

    DefaultMutableTreeNode node1 = new DefaultMutableTreeNode("node1");
    rootNode.add(node1);

    DefaultMutableTreeNode node2 = new DefaultMutableTreeNode("node2");
    rootNode.add(node2);

    rootNode.add(new DefaultMutableTreeNode("node3"));

    tree.setSelectionPaths(new TreePath[] {new TreePath(node1), new TreePath(node2)});

    TreeNode[] selection = (TreeNode[])myPane.getData(SELECTED_TREE_NODES.getName());
    assertThat(selection).asList().containsAllOf(node1, node2);
  }
}