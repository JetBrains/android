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

import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getProjectSystem;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;

import com.android.tools.idea.navigator.nodes.android.AndroidBuildScriptsGroupNode;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ProjectSystemService;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.util.PlatformIcons;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidViewProjectNode extends ProjectViewNode<Project> {

  public AndroidViewProjectNode(@NotNull Project project,
                                @NotNull ViewSettings settings) {
    super(project, project, settings);
  }

  @Override
  @NotNull
  public Collection<? extends AbstractTreeNode<?>> getChildren() {
    assert myProject != null;
    ViewSettings settings = getSettings();
    AndroidProjectSystem projectSystem = ProjectSystemService.getInstance(myProject).getProjectSystem();
    // Android project view cannot build its usual structure without build models available and instead builds a special fallback
    // view when they are no available.  Doing so too soon results in re-loadig the tree view twice while opening a project and losing
    // the state of the tree persisted when closing the project last time.  Therefore we delay responding to getChildren() request
    // until models are available or an attempt to sync has failed.  Since this code run in a read action we respect any attempts to
    // begin a write action while waiting.
    AndroidViewProjectNodeUtil.maybeWaitForAnySyncOutcomeInterruptibly(projectSystem.getSyncManager());
    List<AbstractTreeNode<?>> children =
      ModuleNodeUtils.createChildModuleNodes(myProject, projectSystem.getSubmodules(), settings);

    // If this is a gradle project, and its sync failed, then we attempt to show project root as a folder so that the files
    // are still visible. See https://code.google.com/p/android/issues/detail?id=76564
    boolean lastSyncFailed = !ProjectSystemUtil.getSyncManager(myProject).getLastSyncResult().isSuccessful();

    if (children.isEmpty()) {
      PsiDirectory folder = PsiManager.getInstance(myProject).findDirectory(myProject.getBaseDir());
      if (folder != null) {
        children.add(new PsiDirectoryNode(myProject, folder, settings));
      }
    }

    if (getProjectSystem(myProject).getBuildConfigurationSourceProvider() != null) {
      children.add(new AndroidBuildScriptsGroupNode(myProject, settings));
    }

    // TODO: What about files in the base project directory
    return children;
  }

  @Override
  @Nullable
  public String toTestString(@Nullable Queryable.PrintInfo printInfo) {
    assert myProject != null;
    return String.format("%1$s", myProject.getName());
  }

  /**
   * Copy of {@link com.intellij.ide.projectView.impl.nodes.AbstractProjectNode#update(PresentationData)}
   */
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

    if (index.isInContent(file) || index.isInLibrary(file) ||
        (projectRootFolder != null && isAncestor(projectRootFolder, file, false))) {
      return true;
    }

    return AndroidViewNodeProvider.getProviders().stream().anyMatch(it -> it.projectContainsExternalFile(myProject, file));
  }
}
