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

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseTreeStructure;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.model.PsModel;
import com.google.common.collect.Lists;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.ide.util.treeView.TreeVisitor;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

public abstract class AbstractPsNodeTreeBuilder extends AbstractBaseTreeBuilder {
  public AbstractPsNodeTreeBuilder(@NotNull JTree tree,
                                   @NotNull DefaultTreeModel treeModel,
                                   @NotNull AbstractBaseTreeStructure treeStructure) {
    super(tree, treeModel, treeStructure);
  }

  public void updateSelection() {
    updateSelection(null);
  }

  public void updateSelection(@Nullable MatchingNodeCollector collector) {
    Set<Object> selectedElements = getSelectedElements();
    if (selectedElements.size() == 1) {
      Object selection = getFirstItem(selectedElements);
      if (selection instanceof AbstractPsModelNode) {
        AbstractPsModelNode<?> node = (AbstractPsModelNode)selection;
        List<?> models = node.getModels();
        Object model = models.get(0);
        if (model instanceof PsModel) {
          selectMatchingNodes((PsModel)model, collector, false);
          return;
        }
      }
    }
    if (collector != null) {
      collector.done(Collections.emptyList());
    }
  }

  public void selectMatchingNodes(@NotNull PsModel model, boolean scroll) {
    selectMatchingNodes(model, null, scroll);
  }

  private void selectMatchingNodes(@NotNull final PsModel model, @Nullable final MatchingNodeCollector collector, final boolean scroll) {
    getInitialized().doWhenDone(() -> {
      final List<AbstractPsModelNode> toSelect = Lists.newArrayList();
      accept(AbstractPsModelNode.class, new TreeVisitor<AbstractPsModelNode>() {
        @Override
        public boolean visit(@NotNull AbstractPsModelNode node) {
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
      Runnable onDone = () -> {
        List<SimpleNode> toExpand = Lists.newArrayList();
        for (AbstractPsModelNode node : toSelect) {
          SimpleNode parent = node.getParent();
          if (parent != null) {
            toExpand.add(parent);
          }
        }
        expand(toExpand.toArray(), null);
        if (collector != null) {
          collector.done(collector.matchingNodes);
        }
      };
      getUi().userSelect(toSelect.toArray(), new UserRunnable(onDone), false, scroll);
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
    @NotNull final List<AbstractPsModelNode> matchingNodes = Lists.newArrayList();

    void onMatchingNodeFound(@NotNull AbstractPsModelNode node) {
      matchingNodes.add(node);
    }

    protected abstract void done(@NotNull List<AbstractPsModelNode> matchingNodes);
  }
}
