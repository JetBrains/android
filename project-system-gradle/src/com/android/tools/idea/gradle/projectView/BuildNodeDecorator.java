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

import static com.android.tools.idea.gradle.util.AndroidProjectUtilKt.isAndroidProject;

import com.android.tools.idea.gradle.model.IdeAndroidProject;
import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import java.io.File;

/**
 * This node, in addition to displaying a directory in the "Project" view, changes the default text of the node (the path of the folder)
 * with the word "build" if:
 * <ul>
 * <li>the directory is the "build directory" specified in Gradle</li>
 * <li>the build directory is outside of the module's content root</li>
 * </ul>
 * The reason to do this is that it is useless (and confusing) to know the display the path of the build directory, just marking the
 * node as "build" is good enough.
 */
public class BuildNodeDecorator implements ProjectViewNodeDecorator {
  @Override
  public void decorate(ProjectViewNode node, PresentationData data) {
    if (!(node instanceof PsiDirectoryNode)) {
      return;
    }

    final PsiDirectoryNode psiDirectoryNode = (PsiDirectoryNode)node;
    PsiDirectory directory = psiDirectoryNode.getValue();
    if (directory == null || !directory.isValid()) {
      return;
    }

    final Project project = directory.getProject();
    if (!isAndroidProject(project)) {
      return;
    }

    // If the build dir is inside the module's content root, ProjectRootsUtil.isModuleContentRoot will return false. The reason is that when
    // we set up the project during a sync, we don't create additional content roots if the build dir is inside the module.
    final VirtualFile folder = directory.getVirtualFile();
    if (!ProjectRootsUtil.isModuleContentRoot(folder, project)) {
      return;
    }

    Object parentValue = psiDirectoryNode.getParent().getValue();
    if (!(parentValue instanceof Module)) {
      return;
    }

    Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(folder);
    if (module == null && !module.isDisposed()) {
      return;
    }

    GradleAndroidModel gradleModel = GradleAndroidModel.get(module);
    IdeAndroidProject androidProject = gradleModel != null ? gradleModel.getAndroidProject() : null;
    if (androidProject == null) {
      return;
    }

    File buildFolderPath = androidProject.getBuildFolder();
    File folderPath = VfsUtilCore.virtualToIoFile(folder);
    if (FileUtil.filesEqual(buildFolderPath, folderPath)) {
      data.clearText();
      data.addText(folder.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
