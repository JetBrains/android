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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.issues.module;

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractBaseTreeBuilder;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultTreeModel;
import java.util.List;

public class IssuesTreeBuilder extends AbstractBaseTreeBuilder {
  public IssuesTreeBuilder(@NotNull Tree tree, @NotNull DefaultTreeModel treeModel) {
    super(tree, treeModel, new IssuesTreeStructure());
  }

  public void display(@NotNull List<PsIssue> issues) {
    AbstractTreeStructure treeStructure = getUi().getTreeStructure();
    if (treeStructure instanceof IssuesTreeStructure) {
      ((IssuesTreeStructure)treeStructure).display(issues);
      expandAllNodes();
    }
  }
}
