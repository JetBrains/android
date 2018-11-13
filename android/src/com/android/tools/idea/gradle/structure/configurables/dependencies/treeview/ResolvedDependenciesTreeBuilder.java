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
import com.android.tools.idea.gradle.structure.model.PsBaseDependency;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import org.jetbrains.annotations.NotNull;

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
}
