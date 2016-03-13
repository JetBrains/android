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

import com.android.tools.idea.gradle.structure.configurables.ui.PsdUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.PsModel;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.google.common.collect.Lists;
import com.intellij.ide.util.treeView.*;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.List;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;
import static com.intellij.util.ui.tree.TreeUtil.collapseAll;

public class ResolvedDependenciesTreeBuilder extends AbstractTreeBuilder {
  private static final TreePath[] EMPTY_TREE_PATH = new TreePath[0];

  @NotNull private final DependencySelection myDependencySelectionSource;
  @NotNull private final DependencySelection myDependencySelectionDestination;

  public ResolvedDependenciesTreeBuilder(@NotNull PsAndroidModule moduleModel,
                                         @NotNull JTree tree,
                                         @NotNull DefaultTreeModel treeModel,
                                         @NotNull DependencySelection dependencySelectionSource,
                                         @NotNull DependencySelection dependencySelectionDestination) {
    super(tree, treeModel, new ResolvedDependenciesTreeStructure(moduleModel), IndexComparator.INSTANCE);
    myDependencySelectionSource = dependencySelectionSource;
    myDependencySelectionDestination = dependencySelectionDestination;

    PsdUISettings.ChangeListener changeListener = new PsdUISettings.ChangeListener() {
      @Override
      public void settingsChanged(@NotNull PsdUISettings settings) {
        AbstractTreeStructure treeStructure = getTreeStructure();

        if (treeStructure instanceof ResolvedDependenciesTreeStructure) {
          final PsAndroidDependency selected = myDependencySelectionSource.getSelection();

          boolean needsUpdate = ((ResolvedDependenciesTreeStructure)treeStructure).settingsChanged();

          if (needsUpdate) {
            ActionCallback actionCallback = ResolvedDependenciesTreeBuilder.this.queueUpdate();
            actionCallback.doWhenDone(new Runnable() {
              @Override
              public void run() {
                myDependencySelectionDestination.setSelection(selected);
              }
            });
          }
        }
      }
    };
    PsdUISettings.getInstance().addListener(changeListener, this);
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
      TreeUtil.expandAll(tree);
      getReady(this).doWhenDone(new Runnable() {
        @Override
        public void run() {
          PsAndroidDependency selection = myDependencySelectionSource.getSelection();
          myDependencySelectionDestination.setSelection(selection);
        }
      });
    }
  }

  public void collapseAllNodes() {
    JTree tree = getTree();
    if (tree != null) {
      collapseAll(tree, 1);
      tree.setSelectionPaths(EMPTY_TREE_PATH);
    }
  }

  public void updateSelection() {
    Set<Object> selectedElements = getSelectedElements();
    if (selectedElements.size() == 1) {
      Object selection = getFirstItem(selectedElements);
      if (selection instanceof AbstractPsdNode) {
        AbstractPsdNode<?> node = (AbstractPsdNode)selection;
        List<?> models = node.getModels();
        if (models.size() == 1) {
          Object model = models.get(0);
          if (model instanceof PsModel) {
            setSelection((PsModel)model, false);
          }
        }
      }
    }
  }

  public void clearSelection() {
    JTree tree = getTree();
    if (tree != null) {
      tree.setSelectionPaths(EMPTY_TREE_PATH);
    }
  }

  public void setSelection(@NotNull final PsModel model, final boolean scroll) {
    getInitialized().doWhenDone(new Runnable() {
      @Override
      public void run() {
        final List<AbstractPsdNode> toSelect = Lists.newArrayList();
        accept(AbstractPsdNode.class, new TreeVisitor<AbstractPsdNode>() {
          @Override
          public boolean visit(@NotNull AbstractPsdNode node) {
            if (node.matches(model)) {
              toSelect.add(node);
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
          }
        };
        getUi().userSelect(toSelect.toArray(), new UserRunnable(onDone), false, scroll);
      }
    });
  }

  private class UserRunnable implements Runnable {
    @Nullable private final Runnable myRunnable;

    UserRunnable(@Nullable Runnable runnable) {
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
}
