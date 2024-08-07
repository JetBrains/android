/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.treeview;

import com.google.common.collect.Lists;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings;
import com.google.idea.blaze.base.settings.BlazeImportSettingsManager;
import com.intellij.ide.projectView.ProjectViewSettings;
import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode;
import com.intellij.ide.projectView.impl.nodes.ProjectViewProjectNode;
import com.intellij.ide.scratch.ScratchesNamedScope;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import java.io.File;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * Modifies the project view:
 *
 * <p>- Replaces the root with a single workspace root - Removes rendering of module names and
 * source roots
 */
public class BlazeTreeStructureProvider implements TreeStructureProvider, DumbAware {

  @Override
  public Collection<AbstractTreeNode<?>> modify(
      AbstractTreeNode<?> parent, Collection<AbstractTreeNode<?>> children, ViewSettings settings) {
    Project project = parent.getProject();
    if (!Blaze.isBlazeProject(project)) {
      return children;
    }

    if (!(parent instanceof ProjectViewProjectNode)) {
      return children;
    }
    WorkspaceRootNode rootNode = createRootNode(project, settings);
    if (rootNode == null) {
      return children;
    }

    Collection<AbstractTreeNode<?>> result = Lists.newArrayList();
    result.add(rootNode);
    for (AbstractTreeNode<?> treeNode : children) {
      if (keepOriginalNode(treeNode)) {
        result.add(treeNode);
      }
    }
    return result;
  }

  /**
   * We replace the project tree with our own list of nodes, but keep some of the originals
   * (external libraries, scratches).
   */
  private static boolean keepOriginalNode(AbstractTreeNode<?> node) {
    return node instanceof ExternalLibrariesNode
        || ScratchesNamedScope.scratchesAndConsoles().equals(node.getValue());
  }

  @Nullable
  private WorkspaceRootNode createRootNode(Project project, ViewSettings settings) {
    BlazeImportSettings importSettings =
        BlazeImportSettingsManager.getInstance(project).getImportSettings();
    if (importSettings != null) {
      WorkspaceRoot workspaceRoot = WorkspaceRoot.fromImportSettings(importSettings);
      File fdir = workspaceRoot.directory();
      VirtualFile vdir = LocalFileSystem.getInstance().findFileByIoFile(fdir);
      if (vdir != null) {
        final PsiManager psiManager = PsiManager.getInstance(project);
        PsiDirectory directory = psiManager.findDirectory(vdir);
        return new WorkspaceRootNode(project, workspaceRoot, directory, wrapViewSettings(settings));
      }
    }
    return null;
  }

  private ViewSettings wrapViewSettings(final ViewSettings original) {
    return new ProjectViewSettings() {
      @Override
      public boolean isShowMembers() {
        return original.isShowMembers();
      }

      @Override
      public boolean isStructureView() {
        return original.isStructureView();
      }

      @Override
      public boolean isShowModules() {
        return original.isShowModules();
      }

      @Override
      public boolean isFlattenPackages() {
        return false;
      }

      @Override
      public boolean isAbbreviatePackageNames() {
        return false;
      }

      @Override
      public boolean isHideEmptyMiddlePackages() {
        return original.isHideEmptyMiddlePackages();
      }

      @Override
      public boolean isShowLibraryContents() {
        return original.isShowLibraryContents();
      }

      @Override
      public boolean isShowExcludedFiles() {
        if (original instanceof ProjectViewSettings) {
          return ((ProjectViewSettings) original).isShowExcludedFiles();
        }
        return true;
      }
    };
  }
}
