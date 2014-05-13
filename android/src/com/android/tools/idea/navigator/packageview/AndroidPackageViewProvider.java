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
package com.android.tools.idea.navigator.packageview;

import com.android.tools.idea.gradle.util.Projects;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.PackageViewPane;
import com.intellij.ide.projectView.impl.nodes.PackageViewModuleNode;
import com.intellij.ide.projectView.impl.nodes.PackageViewProjectNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class AndroidPackageViewProvider implements TreeStructureProvider {
  @NotNull
  @Override
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent,
                                             @NotNull Collection<AbstractTreeNode> children,
                                             ViewSettings settings) {
    Project project = parent.getProject();
    if (!isProviderEnabled(parent, project)) {
      return children;
    }

    List<AbstractTreeNode> list = Lists.newArrayListWithExpectedSize(children.size());
    for (AbstractTreeNode child : children) {
      if (!(child instanceof PackageViewModuleNode)) {
        list.add(child);
        continue;
      }

      Module module = ((PackageViewModuleNode)child).getValue();
      list.add(new AndroidModuleNode(project, module, settings));
    }

    return list;
  }

  @Contract("_, null -> false")
  protected boolean isProviderEnabled(AbstractTreeNode parent, @Nullable Project project) {
    if (project == null
      || !PackageViewPane.ID.equals(ProjectView.getInstance(project).getCurrentViewId())
      || !Projects.isGradleProject(project)) {
      return false;
    }

    if (!(parent instanceof PackageViewProjectNode)) {
      return false;
    }

    return true;
  }

  @Nullable
  @Override
  public Object getData(Collection<AbstractTreeNode> selected, String dataName) {
    return null;
  }
}
