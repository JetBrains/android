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
package com.android.tools.idea.gradle.structure.configurables.dependencies.treeview;

import static com.intellij.util.containers.ContainerUtil.getFirstItem;

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseTreeStructure;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.model.PsModel;
import com.google.common.collect.Lists;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.ide.util.treeView.TreeVisitor;
import com.intellij.openapi.util.ActionCallback;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractPsNodeTreeBuilder extends AbstractBaseTreeBuilder {
  public AbstractPsNodeTreeBuilder(@NotNull JTree tree,
                                   @NotNull DefaultTreeModel treeModel,
                                   @NotNull AbstractBaseTreeStructure treeStructure) {
    super(tree, treeModel, treeStructure);
  }

  @Nullable
  public AbstractPsModelNode<?> getSelectedNode() {
    Set<Object> selectedElements = getSelectedElements();
    if (selectedElements.size() == 1) {
      Object selection = getFirstItem(selectedElements);
      if (selection instanceof AbstractPsModelNode) {
        return (AbstractPsModelNode)selection;
      }
    }
    return null;
  }

  @Nullable
  private static PsModel getFirstModel(@NotNull AbstractPsModelNode<?> node) {
    List<?> models = node.getModels();
    Object model = models.get(0);
    if (model instanceof PsModel) {
      return (PsModel)model;
    }
    return null;
  }

  public static abstract class MatchingNodeCollector {
    @NotNull final List<AbstractPsModelNode> matchingNodes = Lists.newArrayList();

    void onMatchingNodeFound(@NotNull AbstractPsModelNode node) {
      matchingNodes.add(node);
    }

    protected abstract void done(@NotNull List<AbstractPsModelNode> matchingNodes);
  }
}
