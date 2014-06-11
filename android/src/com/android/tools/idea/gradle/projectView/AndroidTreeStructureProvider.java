/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.projectView;

import com.google.common.collect.Lists;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class AndroidTreeStructureProvider implements TreeStructureProvider {
  @Override
  @NotNull
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent,
                                             @NotNull Collection<AbstractTreeNode> children,
                                             ViewSettings settings) {
    final Project project = parent.getProject();

    if (project == null || !ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return children;
    }
    List<AbstractTreeNode> newChildren = Lists.newArrayList();
    for (AbstractTreeNode child : children) {
      // We replace the default "directory node" with our own, to control the text displayed by the node.
      if (child instanceof PsiDirectoryNode) {
        AndroidPsiDirectoryNode newChild = new AndroidPsiDirectoryNode(child.getProject(), ((PsiDirectoryNode)child).getValue(), settings);
        newChildren.add(newChild);
        continue;
      }
      newChildren.add(child);
    }
    return newChildren;
  }

  @Nullable
  @Override
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }
}
