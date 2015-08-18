/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.subset;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.ide.projectView.impl.ProjectRootsUtil.isInSource;
import static com.intellij.ide.projectView.impl.ProjectRootsUtil.isModuleSourceRoot;
import static com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE_ARRAY;

public class IncludeModuleForFileAction extends AnAction {
  public IncludeModuleForFileAction() {
    super("Find and Add Module");
  }

  @Override
  public void update(AnActionEvent e) {
    boolean show = false;
    VirtualFile file = null;
    Project project = e.getProject();
    if (project != null) {
      file = findTarget(e, project);
      if (file != null) {
        show = !isInSource(file, project);
      }
    }
    Presentation presentation = e.getPresentation();
    presentation.setVisible(show);

    if (file != null) {
      String type = file.isDirectory() ? "Directory" : "File";
      presentation.setText(String.format("Find and Add Module Containing Selected %1$s as Source", type));
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      VirtualFile file = findTarget(e, project);
      if (file != null) {
        ProjectSubset.getInstance(project).findAndIncludeModuleContainingSourceFile(file);
      }
    }
  }

  @Nullable
  private static VirtualFile findTarget(@NotNull AnActionEvent e, @NotNull Project project) {
    VirtualFile[] virtualFiles = VIRTUAL_FILE_ARRAY.getData(e.getDataContext());
    if (virtualFiles != null && virtualFiles.length == 1) {
      VirtualFile target = virtualFiles[0];
      if (isModuleSourceRoot(target, project) || project.getBaseDir().equals(target)) {
        // Module folders and project folder are ignored.
        return null;
      }
      ProjectFileIndex projectIndex = ProjectRootManager.getInstance(project).getFileIndex();
      if (projectIndex.isExcluded(target)) {
        // Excluded folders are ignored.
        return null;
      }
      return target;
    }
    return null;
  }
}
