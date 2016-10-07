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
package com.android.tools.idea.navigator.nodes;

import com.android.tools.idea.gradle.facet.NativeAndroidGradleFacet;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.Projects;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AndroidViewProjectNode extends ProjectViewNode<Project> {
  private final AndroidProjectViewPane myProjectViewPane;

  public AndroidViewProjectNode(@NotNull Project project,
                                @NotNull ViewSettings viewSettings,
                                @NotNull AndroidProjectViewPane projectViewPane) {
    super(project, project, viewSettings);
    myProjectViewPane = projectViewPane;
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    Project project = getProject();
    ViewSettings settings = getSettings();

    // add a node for every module
    // TODO: make this conditional on getSettings().isShowModules(), otherwise collapse them all at the root
    List<Module> modules = Arrays.asList(ModuleManager.getInstance(project).getModules());
    List<AbstractTreeNode> children = Lists.newArrayListWithExpectedSize(modules.size());
    for (Module module : modules) {
      if (GradleUtil.isRootModuleWithNoSources(module)) {
        // exclude the root module if it doesn't have any source roots
        // The most common organization of Gradle projects has an empty root module that is simply a container for other modules.
        // If we detect such a module, then we don't show it..
        continue;
      }

      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      NativeAndroidGradleFacet nativeAndroidFacet = NativeAndroidGradleFacet.getInstance(module);
      if (androidFacet != null && androidFacet.getAndroidModel() != null) {
        children.add(new AndroidModuleNode(project, module, settings, myProjectViewPane));
      }
      else if (nativeAndroidFacet != null && nativeAndroidFacet.getNativeAndroidGradleModel() != null ) {
        children.add(new NativeAndroidModuleNode(project, module, settings));
      }
      else {
        children.add(new NonAndroidModuleNode(project, module, settings));
      }
    }

    // If this is a gradle project, and its sync failed, then we attempt to show project root as a folder so that the files
    // are still visible. See https://code.google.com/p/android/issues/detail?id=76564
    if (children.isEmpty() && Projects.isBuildWithGradle(project) && Projects.lastGradleSyncFailed(project)) {
      PsiDirectory dir = PsiManager.getInstance(project).findDirectory(project.getBaseDir());
      if (dir != null) {
        children.add(new PsiDirectoryNode(project, dir, settings));
      }
    }

    if (Projects.isBuildWithGradle(project)) {
      children.add(new AndroidBuildScriptsGroupNode(project, settings));
    }

    ExternalBuildFilesGroupNode externalBuildFilesNode = new ExternalBuildFilesGroupNode(project, settings);
    if (!externalBuildFilesNode.getChildren().isEmpty()) {
      children.add(externalBuildFilesNode);
    }

    // TODO: What about files in the base project directory

    // TODO: Do we want to show the External Libraries Node or a Dependencies node

    return children;
  }

  @Nullable
  @Override
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    return String.format("%1$s", getProject().getName());
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    return super.equals(o);
  }

  /** Copy of {@link com.intellij.ide.projectView.impl.nodes.AbstractProjectNode#update(com.intellij.ide.projectView.PresentationData)} */
  @Override
  protected void update(PresentationData presentation) {
    presentation.setIcon(PlatformIcons.PROJECT_ICON);
    presentation.setPresentableText(getProject().getName());
  }

  /** Copy of {@link com.intellij.ide.projectView.impl.nodes.AbstractProjectNode#contains(com.intellij.openapi.vfs.VirtualFile)}*/
  @Override
  public boolean contains(@NotNull VirtualFile file) {
    ProjectFileIndex index = ProjectRootManager.getInstance(getProject()).getFileIndex();
    final VirtualFile baseDir = getProject().getBaseDir();
    return index.isInContent(file) || index.isInLibraryClasses(file) || index.isInLibrarySource(file) ||
           (baseDir != null && VfsUtilCore.isAncestor(baseDir, file, false));
  }
}
