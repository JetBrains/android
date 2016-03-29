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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractPsNodeTreeBuilder;
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AbstractDependencyNode;
import com.android.tools.idea.gradle.structure.model.PsProject;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;

public class TargetModelsTreeBuilder extends AbstractPsNodeTreeBuilder {
  public TargetModelsTreeBuilder(@NotNull PsProject project,
                                 @NotNull JTree tree,
                                 @NotNull DefaultTreeModel treeModel) {
    super(tree, treeModel, new TargetModelsTreeStructure(project));
  }

  public void displayTargetModules(@NotNull List<AbstractDependencyNode<? extends PsAndroidDependency>> dependencyNodes) {
    AbstractTreeStructure treeStructure = getTreeStructure();
    if (treeStructure instanceof TargetModelsTreeStructure) {
      ((TargetModelsTreeStructure)treeStructure).displayTargetModules(dependencyNodes);
      queueUpdate();
    }
  }
}
