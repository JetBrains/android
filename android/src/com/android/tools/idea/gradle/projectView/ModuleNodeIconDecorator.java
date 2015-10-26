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

import com.android.tools.idea.gradle.testing.TestArtifactSearchScopes;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.ColoredTreeCellRenderer;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;

import static com.android.tools.idea.gradle.util.GradleUtil.getModuleIcon;
import static com.intellij.ide.projectView.impl.ProjectRootsUtil.isSourceRoot;
import static com.intellij.openapi.module.ModuleUtilCore.isModuleDir;

/**
 * Provides custom icons for modules based on the module type.
 */
public class ModuleNodeIconDecorator implements ProjectViewNodeDecorator {
  @Override
  public void decorate(ProjectViewNode node, PresentationData data) {
    if (!(node instanceof PsiDirectoryNode)) {
      return;
    }

    final PsiDirectoryNode psiDirectoryNode = (PsiDirectoryNode)node;
    PsiDirectory psiDirectory = psiDirectoryNode.getValue();
    assert psiDirectory != null;
    Project project = psiDirectory.getProject();
    if (!ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)) {
      return;
    }

    VirtualFile folder = psiDirectory.getVirtualFile();
    Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(folder);
    if (module != null) {
      if (isModuleDir(module, folder)) {
        data.setIcon(getModuleIcon(module));
      }
      else if (isSourceRoot(folder, project)) {
        TestArtifactSearchScopes testScopes = TestArtifactSearchScopes.get(module);
        if (testScopes != null && testScopes.isAndroidTestSource(folder)) {
          data.setIcon(AndroidIcons.AndroidTestRoot);
        }
      }
    }
  }

  @Override
  public void decorate(PackageDependenciesNode node, ColoredTreeCellRenderer cellRenderer) {
  }
}
