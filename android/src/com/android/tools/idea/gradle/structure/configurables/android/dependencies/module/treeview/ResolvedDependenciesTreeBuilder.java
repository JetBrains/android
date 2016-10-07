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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractPsNodeTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

public class ResolvedDependenciesTreeBuilder extends AbstractPsNodeTreeBuilder {
  @NotNull private final DependencySelection myDependencySelectionSource;
  @NotNull private final DependencySelection myDependencySelectionDestination;

  public ResolvedDependenciesTreeBuilder(@NotNull PsAndroidModule module,
                                         @NotNull JTree tree,
                                         @NotNull DefaultTreeModel treeModel,
                                         @NotNull DependencySelection dependencySelectionSource,
                                         @NotNull DependencySelection dependencySelectionDestination) {
    super(tree, treeModel, new ResolvedDependenciesTreeStructure(module));
    myDependencySelectionSource = dependencySelectionSource;
    myDependencySelectionDestination = dependencySelectionDestination;

    PsUISettings.ChangeListener changeListener = settings -> {
      AbstractTreeStructure treeStructure = getTreeStructure();

      if (treeStructure instanceof ResolvedDependenciesTreeStructure) {
        boolean needsUpdate = ((ResolvedDependenciesTreeStructure)treeStructure).settingsChanged();
        if (needsUpdate) {
          queueUpdateAndRestoreSelection();
        }
      }
    };
    PsUISettings.getInstance().addListener(changeListener, this);
  }

  @Override
  protected void onAllNodesExpanded() {
    getReady(this).doWhenDone(() -> {
      PsAndroidDependency selection = myDependencySelectionSource.getSelection();
      myDependencySelectionDestination.setSelection(selection);
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
    PsAndroidDependency selected = myDependencySelectionSource.getSelection();
    queueUpdate().doWhenDone(() -> myDependencySelectionDestination.setSelection(selected));
  }
}
