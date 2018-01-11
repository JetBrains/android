/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.fileTypes;

import com.intellij.ide.IconProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.idea.gradle.util.GradleUtil.getModuleIcon;
import static com.intellij.ide.projectView.impl.ProjectRootsUtil.isModuleContentRoot;

/** Icon customizations when running in Android Studio */
public class AndroidStudioIconProvider extends IconProvider {
  @Nullable
  @Override
  public Icon getIcon(@NotNull PsiElement element, @Iconable.IconFlags int flags) {
    // Use Android Studio icons for module's root
    if (element instanceof PsiDirectory) {
      PsiDirectory psiDirectory = (PsiDirectory)element;
      VirtualFile virtualDirectory = psiDirectory.getVirtualFile();
      Project project = psiDirectory.getProject();
      if (isModuleContentRoot(virtualDirectory, project)) {
        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        Module module = projectFileIndex.getModuleForFile(virtualDirectory);
        if (module != null) {
          return getModuleIcon(module);
        }
      }
    }

    return null;
  }
}
