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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview;

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.PsModel;
import com.google.common.collect.Lists;
import com.intellij.ide.util.treeView.*;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.tree.TreeUtil.collapseAll;

public abstract class AbstractBaseTreeBuilder extends AbstractTreeBuilder {
  private static final TreePath[] EMPTY_TREE_PATH = new TreePath[0];

  public AbstractBaseTreeBuilder(@NotNull JTree tree,
                                 @NotNull DefaultTreeModel treeModel,
                                 @NotNull AbstractBaseTreeStructure treeStructure) {
    super(tree, treeModel, treeStructure, IndexComparator.INSTANCE);
  }

  @Override
  public boolean isToEnsureSelectionOnFocusGained() {
    return false;
  }

  @Override
  protected boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
    if (nodeDescriptor instanceof AbstractPsdNode) {
      return ((AbstractPsdNode)nodeDescriptor).isAutoExpandNode();
    }
    return super.isAutoExpandNode(nodeDescriptor);
  }

  @Override
  protected boolean isSmartExpand() {
    return true;
  }

  public void expandAllNodes() {
    JTree tree = getTree();
    if (tree != null) {
      clearSelection();
      TreeUtil.expandAll(tree);
      onAllNodesExpanded();
    }
  }

  protected abstract void onAllNodesExpanded();

  public void collapseAllNodes() {
    JTree tree = getTree();
    if (tree != null) {
      collapseAll(tree, 1);
      clearSelection(tree);
    }
  }

  public void clearSelection() {
    JTree tree = getTree();
    if (tree != null) {
      clearSelection(tree);
    }
  }

  private static void clearSelection(@NotNull JTree tree) {
    tree.setSelectionPaths(EMPTY_TREE_PATH);
  }

  public void updateSelection() {
    updateSelection(null);
  }

  public void updateSelection(@Nullable MatchingNodeCollector collector) {
    Set<Object> selectedElements = getSelectedElements();
    if (selectedElements.size() == 1) {
      Object selection = getFirstItem(selectedElements);
      if (selection instanceof AbstractPsdNode) {
        AbstractPsdNode<?> node = (AbstractPsdNode)selection;
        List<?> models = node.getModels();
        Object model = models.get(0);
        if (model instanceof PsModel) {
          selectMatchingNodes((PsModel)model, collector, false);
          return;
        }
      }
    }
    if (collector != null) {
      collector.done(Collections.<AbstractPsdNode>emptyList());
    }
  }

  public void selectMatchingNodes(@NotNull PsModel model, boolean scroll) {
    selectMatchingNodes(model, null, scroll);
  }

  private void selectMatchingNodes(@NotNull final PsModel model, @Nullable final MatchingNodeCollector collector, final boolean scroll) {
    getInitialized().doWhenDone(new Runnable() {
      @Override
      public void run() {
        final List<AbstractPsdNode> toSelect = Lists.newArrayList();
        accept(AbstractPsdNode.class, new TreeVisitor<AbstractPsdNode>() {
          @Override
          public boolean visit(@NotNull AbstractPsdNode node) {
            if (node.matches(model)) {
              toSelect.add(node);
              if (collector != null) {
                collector.onMatchingNodeFound(node);
              }
            }
            return false;
          }
        });

        if (isDisposed()) {
          return;
        }
        // Expand the parents of all selected nodes, so they can be visible to the user.
        Runnable onDone = new Runnable() {
          @Override
          public void run() {
            List<SimpleNode> toExpand = Lists.newArrayList();
            for (AbstractPsdNode node : toSelect) {
              SimpleNode parent = node.getParent();
              if (parent != null) {
                toExpand.add(parent);
              }
            }
            expand(toExpand.toArray(), null);
            if (collector != null) {
              collector.done(collector.matchingNodes);
            }
          }
        };
        getUi().userSelect(toSelect.toArray(), new UserRunnable(onDone), false, scroll);
      }
    });
  }

  protected class UserRunnable implements Runnable {
    @Nullable private final Runnable myRunnable;

    public UserRunnable(@Nullable Runnable runnable) {
      myRunnable = runnable;
    }

    @Override
    public void run() {
      if (myRunnable != null) {
        AbstractTreeUi treeUi = getUi();
        if (treeUi != null) {
          treeUi.executeUserRunnable(myRunnable);
        }
        else {
          myRunnable.run();
        }
      }
    }
  }

  public static abstract class MatchingNodeCollector {
    @NotNull final List<AbstractPsdNode> matchingNodes = Lists.newArrayList();

    void onMatchingNodeFound(@NotNull AbstractPsdNode node) {
      matchingNodes.add(node);
    }

    protected abstract void done(@NotNull List<AbstractPsdNode> matchingNodes);
  }
}
