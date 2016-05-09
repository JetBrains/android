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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.resolved.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractPsNodeTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;

public class ResolvedDependenciesTreeBuilder extends AbstractPsNodeTreeBuilder {
  public ResolvedDependenciesTreeBuilder(@NotNull PsAndroidModule module,
                                         @NotNull JTree tree,
                                         @NotNull DefaultTreeModel treeModel) {
    super(tree, treeModel, new ResolvedDependenciesTreeStructure(module));

    PsUISettings.ChangeListener changeListener = settings -> {
      AbstractTreeStructure treeStructure = getTreeStructure();

      if (treeStructure instanceof ResolvedDependenciesTreeStructure) {
        boolean needsUpdate = ((ResolvedDependenciesTreeStructure)treeStructure).settingsChanged();
        if (needsUpdate) {
          queueUpdate();
        }
      }
    };
    PsUISettings.getInstance().addListener(changeListener, this);
  }

  public void reset() {
    AbstractTreeStructure treeStructure = getTreeStructure();
    if (treeStructure instanceof ResolvedDependenciesTreeStructure) {
      ((ResolvedDependenciesTreeStructure)treeStructure).reset();
      queueUpdate();
    }
  }
}
