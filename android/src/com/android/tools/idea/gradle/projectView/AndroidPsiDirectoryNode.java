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

import com.android.builder.model.AndroidProject;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.ui.SimpleTextAttributes;
import icons.AndroidIcons;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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
public class AndroidPsiDirectoryNode extends PsiDirectoryNode {
  public AndroidPsiDirectoryNode(@Nullable Project project, @NotNull PsiDirectory value, @NotNull ViewSettings viewSettings) {
    //noinspection ConstantConditions
    super(project, value, viewSettings);
  }

  @SuppressWarnings("ConstantConditions") // remove warnings about passing null as parameter not annotated with @Nullable.
  @Override
  protected void updateImpl(PresentationData data) {
    Project project = getProject();
    if (project == null) {
      super.updateImpl(data);
      return;
    }

    PsiDirectory directory = getValue();
    VirtualFile folder = directory.getVirtualFile();

    Object parentValue = getParentValue();

    // If the build dir is inside the module's content root, ProjectRootsUtil.isModuleContentRoot will return false. The reason is that when
    // we set up the project during a sync, we don't create additional content roots if the build dir is inside the module.
    if (ProjectRootsUtil.isModuleContentRoot(folder, project)) {
      Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(folder);
      if (module != null && parentValue instanceof Module) {
        AndroidProject androidProject = GradleUtil.getAndroidProject(module);
        if (androidProject != null) {
          File buildFolderPath = androidProject.getBuildFolder();
          File folderPath = VfsUtilCore.virtualToIoFile(folder);
          if (FileUtil.filesEqual(buildFolderPath, folderPath)) {
            data.addText(folder.getName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            return;
          }
        }
      }
    }
    super.updateImpl(data);
  }

  @Override
  protected void setupIcon(PresentationData data, PsiDirectory psiDirectory) {
    Project project = psiDirectory.getProject();
    VirtualFile folder = psiDirectory.getVirtualFile();
    Module module = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(folder);
    if (module != null && isModuleFolder(folder, module)) {
      data.setIcon(patchIcon(GradleUtil.getModuleIcon(module), folder));
      return;
    }
    super.setupIcon(data, psiDirectory);
  }

  private static boolean isModuleFolder(@NotNull VirtualFile folder, @NotNull Module module) {
    VirtualFile moduleFile = module.getModuleFile();
    if (moduleFile == null) {
      return false;
    }
    VirtualFile parent = moduleFile.getParent();
    return folder.equals(parent);
  }
}
