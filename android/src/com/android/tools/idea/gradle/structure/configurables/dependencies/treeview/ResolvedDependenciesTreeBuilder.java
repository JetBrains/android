/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.model.PsBaseDependency;
import com.android.tools.idea.gradle.structure.model.PsModel;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.openapi.util.ActionCallback;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResolvedDependenciesTreeBuilder extends AbstractPsNodeTreeBuilder {
  @NotNull private final DependencySelection myDependencySelectionSource;
  @NotNull private final DependencySelection myDependencySelectionDestination;

  public ResolvedDependenciesTreeBuilder(@NotNull PsModule module,
                                         @NotNull JTree tree,
                                         @NotNull DefaultTreeModel treeModel,
                                         @NotNull DependencySelection dependencySelectionSource,
                                         @NotNull DependencySelection dependencySelectionDestination,
                                         @NotNull PsUISettings uiSettings) {
    super(tree, treeModel, new ResolvedDependenciesTreeStructure(module, uiSettings));
    myDependencySelectionSource = dependencySelectionSource;
    myDependencySelectionDestination = dependencySelectionDestination;
  }

  @Override
  protected void onAllNodesExpanded() {
    getReady(this).doWhenDone(() -> {
      PsBaseDependency selection = myDependencySelectionSource.getSelection();
      myDependencySelectionDestination.setSelection(selection != null ? ImmutableList.of(selection) : null);
    });
  }

  public void reset() {
    AbstractTreeStructure treeStructure = getTreeStructure();
    if (treeStructure instanceof ResolvedDependenciesTreeStructure) {
      ((ResolvedDependenciesTreeStructure)treeStructure).reset();
      queueUpdateAndRestoreSelection();
    }
  }

  private void queueUpdateAndRestoreSelection() {
    PsBaseDependency selected = myDependencySelectionSource.getSelection();
    queueUpdate().doWhenDone(() -> myDependencySelectionDestination.setSelection(selected != null ? ImmutableList.of(selected) : null));
  }

  public ActionCallback selectMatchingNodes(@NotNull PsModel model, boolean scroll) {
    return collectMatchingNodes(model, scroll);
  }

  private ActionCallback collectMatchingNodes(@NotNull PsModel model, boolean scroll) {
    ActionCallback result = new ActionCallback();
    getInitialized()
      .doWhenDone(() -> {
        if (isDisposed()) {
          result.setRejected();
          return;
        }

        List<AbstractPsModelNode> toSelect = Lists.newArrayList();
        Queue<AbstractPsModelNode> queue = new ArrayDeque<>();
        queue.add((AbstractPsModelNode)getRootElement());

        while (!queue.isEmpty()) {
          AbstractPsModelNode element = queue.poll();
          if (element.matches(model)) {
            toSelect.add(element);
          }

          if (!(element == getRootElement()
                || element.getParent() instanceof ResolvedDependenciesTreeRootNode
                || element.getParent() instanceof ModuleDependencyNode
                || element.getParent() instanceof AndroidArtifactNode)) {
            continue;
          }
          Arrays.stream(element.getChildren())
            .filter(it -> it instanceof AbstractPsModelNode)
            .forEach(it -> queue.add((AbstractPsModelNode)it));
        }

        // Expand the parents of all selected nodes, so they can be visible to the user.
        Runnable onDone = () -> {
          expandParents(toSelect);
          if (scroll) {
            scrollToFirstSelectedRow();
          }
          result.setDone();
        };
        // NOTE: This is a non-deferred select operation which may fail with a stack overflow
        //       if multiple items are selected on a really large tree.
        getUi().userSelect(toSelect.toArray(), new UserRunnable(onDone), false, false);
      })
      .doWhenRejected(() -> result.setRejected());
    return result;
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
}
