/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.apk.ApkFacet;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.navigator.AndroidProjectViewPane;
import com.android.tools.idea.navigator.nodes.android.AndroidBuildScriptsGroupNode;
import com.android.tools.idea.navigator.nodes.android.AndroidModuleNode;
import com.android.tools.idea.navigator.nodes.apk.ApkModuleNode;
import com.android.tools.idea.navigator.nodes.ndk.ExternalBuildFilesGroupNode;
import com.android.tools.idea.navigator.nodes.ndk.NdkModuleNode;
import com.android.tools.idea.navigator.nodes.other.NonAndroidModuleNode;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesNode;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.isRootModuleWithNoSources;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;

public class AndroidViewProjectNode extends ProjectViewNode<Project> {
  private final AndroidProjectViewPane myProjectViewPane;

  public AndroidViewProjectNode(@NotNull Project project,
                                @NotNull ViewSettings settings,
                                @NotNull AndroidProjectViewPane projectViewPane) {
    super(project, project, settings);
    myProjectViewPane = projectViewPane;
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode> getChildren() {
    assert myProject != null;
    ViewSettings settings = getSettings();

    // add a node for every module
    // TODO: make this conditional on getSettings().isShowModules(), otherwise collapse them all at the root
    List<Module> modules = Arrays.asList(ModuleManager.getInstance(myProject).getModules());
    List<AbstractTreeNode> children = new ArrayList<>(modules.size());
    for (Module module : modules) {
      ApkFacet apkFacet = ApkFacet.getInstance(module);
      if (isRootModuleWithNoSources(module) && apkFacet == null) {
        // exclude the root module if it doesn't have any source roots
        // The most common organization of Gradle projects has an empty root module that is simply a container for other modules.
        // If we detect such a module, then we don't show it..
        continue;
      }

      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      NdkFacet ndkFacet = NdkFacet.getInstance(module);
      if (androidFacet != null && androidFacet.getAndroidModel() != null) {
        children.add(new AndroidModuleNode(myProject, module, settings, myProjectViewPane));
      }
      else if (androidFacet != null && apkFacet != null) {
        children.add(new ApkModuleNode(myProject, module, androidFacet, apkFacet, settings));
        children.add(new ExternalLibrariesNode(myProject, settings));
      }
      else if (ndkFacet != null && ndkFacet.getNdkModuleModel() != null) {
        children.add(new NdkModuleNode(myProject, module, settings));
      }
      else {
        children.add(new NonAndroidModuleNode(myProject, module, settings));
      }
    }

    // If this is a gradle project, and its sync failed, then we attempt to show project root as a folder so that the files
    // are still visible. See https://code.google.com/p/android/issues/detail?id=76564
    boolean buildWithGradle = GradleProjectInfo.getInstance(myProject).isBuildWithGradle();
    if (children.isEmpty() && buildWithGradle && GradleSyncState.getInstance(myProject).lastSyncFailed()) {
      PsiDirectory folder = PsiManager.getInstance(myProject).findDirectory(myProject.getBaseDir());
      if (folder != null) {
        children.add(new PsiDirectoryNode(myProject, folder, settings));
      }
    }

    if (buildWithGradle) {
      children.add(new AndroidBuildScriptsGroupNode(myProject, settings));
    }

    ExternalBuildFilesGroupNode externalBuildFilesNode = new ExternalBuildFilesGroupNode(myProject, settings);
    if (!externalBuildFilesNode.getChildren().isEmpty()) {
      children.add(externalBuildFilesNode);
    }

    // TODO: What about files in the base project directory

    // TODO: Do we want to show the External Libraries Node or a Dependencies node

    return children;
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    assert myProject != null;
    return String.format("%1$s", myProject.getName());
  }

  /** Copy of {@link com.intellij.ide.projectView.impl.nodes.AbstractProjectNode#update(PresentationData)} */
  @Override
  protected void update(@NotNull PresentationData presentation) {
    presentation.setIcon(PlatformIcons.PROJECT_ICON);
    Project project = getProject();
    assert project != null;
    presentation.setPresentableText(project.getName());
  }

  /**
   * Copy of {@link com.intellij.ide.projectView.impl.nodes.AbstractProjectNode#contains(VirtualFile)}
   */
  @Override
  public boolean contains(@NotNull VirtualFile file) {
    assert myProject != null;

    ProjectFileIndex index = ProjectRootManager.getInstance(myProject).getFileIndex();
    VirtualFile projectRootFolder = myProject.getBaseDir();
    return index.isInContent(file) || index.isInLibraryClasses(file) || index.isInLibrarySource(file) ||
           (projectRootFolder != null && isAncestor(projectRootFolder, file, false));
  }
}
