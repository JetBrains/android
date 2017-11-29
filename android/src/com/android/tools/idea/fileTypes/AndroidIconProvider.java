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
package com.android.tools.idea.fileTypes;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.rendering.FlagManager;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.tools.idea.gradle.util.GradleUtil.getModuleIcon;
import static com.intellij.ide.projectView.impl.ProjectRootsUtil.isModuleContentRoot;

public class AndroidIconProvider extends IconProvider {
  @Nullable
  @Override
  public Icon getIcon(@NotNull PsiElement element, @Iconable.IconFlags int flags) {
    if (element instanceof XmlFile) {
      final VirtualFile file = ((XmlFile)element).getVirtualFile();
      if (file == null) {
        return null;
      }
      if (FN_ANDROID_MANIFEST_XML.equals(file.getName())) {
        return AndroidIcons.ManifestFile;
      }
      VirtualFile parent = file.getParent();
      if (parent != null) {
        String parentName = parent.getName();
        // Check whether resource folder is (potentially) a resource folder with qualifiers
        int index = parentName.indexOf('-');
        if (index != -1) {
          FolderConfiguration config = FolderConfiguration.getConfigForFolder(parentName);
          if (config != null && config.getLocaleQualifier() != null && ResourceFolderType.getFolderType(parentName) != null) {
            // If resource folder qualifier specifies locale, show a flag icon
            return FlagManager.get().getFlag(config);
          }
        }
      }
    }
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
