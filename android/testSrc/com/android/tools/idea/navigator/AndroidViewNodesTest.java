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

import com.android.tools.idea.testing.IdeComponents;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidViewNodes}.
 */
public class AndroidViewNodesTest extends IdeaTestCase {
  @Mock private AndroidProjectViewPane myProjectViewPane;
  @Mock private AbstractTreeUi myTreeUi;

  private MyTreeBuilder myTreeBuilder;
  private DefaultMutableTreeNode myRootNode;
  private AndroidViewNodes myAndroidViewNodes;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    ProjectView projectView = IdeComponents.replaceServiceWithMock(getProject(), ProjectView.class);
    when(projectView.getProjectViewPaneById(AndroidProjectViewPane.ID)).thenReturn(myProjectViewPane);

    myRootNode = new DefaultMutableTreeNode();
    DefaultTreeModel treeModel = new DefaultTreeModel(myRootNode);

    myTreeBuilder = new MyTreeBuilder(myTreeUi, treeModel);
    when(myProjectViewPane.getTreeBuilder()).thenReturn(myTreeBuilder);

    when(myTreeUi.getRootNode()).thenReturn(myRootNode);
    when(myTreeUi.getTreeModel()).thenReturn(treeModel);

    myAndroidViewNodes = new AndroidViewNodes();
  }

  public void testFindAndRefreshNode()  {
    DefaultMutableTreeNode node1 = new DefaultMutableTreeNode("node1");
    DefaultMutableTreeNode node2 = new DefaultMutableTreeNode("node2");
    myRootNode.add(node1);
    myRootNode.add(node2);

    // Should find node1.
    myAndroidViewNodes.findAndRefreshNode(getProject(), node -> "node1".equals(node.getUserObject()));

    // Should refresh node1.
    myTreeBuilder.verifyUpdated("node1");
  }

  private static class MyTreeBuilder extends AbstractTreeBuilder {
    @NotNull private final AbstractTreeUi myTreeUi;

    private Object myUpdatedElement;

    MyTreeBuilder(@NotNull AbstractTreeUi treeUi, @NotNull DefaultTreeModel treeModel) {
      myTreeUi = treeUi;
      init(new Tree(), treeModel, mock(AbstractTreeStructure.class), null, false);
    }

    @Override
    @NotNull
    protected AbstractTreeUi createUi() {
      return myTreeUi;
    }

    @Override
    @NotNull
    public ActionCallback queueUpdateFrom(Object element, boolean forceResort, boolean updateStructure) {
      myUpdatedElement = element;
      return ActionCallback.DONE;
    }

    void verifyUpdated(@NotNull Object element) {
      assertSame(element, myUpdatedElement);
    }
  }
}