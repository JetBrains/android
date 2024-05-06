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
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.android.tools.idea.rendering.FlagManager;
import com.intellij.icons.AllIcons;
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
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;
import static com.intellij.ide.projectView.impl.ProjectRootsUtil.isModuleContentRoot;
import static icons.StudioIcons.Shell.Filetree.ANDROID_MODULE;
import static icons.StudioIcons.Shell.Filetree.ANDROID_TEST_ROOT;
import static icons.StudioIcons.Shell.Filetree.FEATURE_MODULE;
import static icons.StudioIcons.Shell.Filetree.INSTANT_APPS;
import static icons.StudioIcons.Shell.Filetree.LIBRARY_MODULE;

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
        return StudioIcons.Shell.Filetree.MANIFEST_FILE;
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

    if (element instanceof PsiDirectory) {
      PsiDirectory psiDirectory = (PsiDirectory)element;
      VirtualFile virtualDirectory = psiDirectory.getVirtualFile();
      Project project = psiDirectory.getProject();
      if (isModuleContentRoot(virtualDirectory, project)) {
        ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        Module module = projectFileIndex.getModuleForFile(virtualDirectory);
        // Only provide icons for modules that are setup by the Android plugin - other modules may be provided for
        // by later providers, we don't assume getModuleIcon returns the correct icon in these cases.
        if (module != null && !module.isDisposed() && ModuleSystemUtil.isLinkedAndroidModule(module)) {
          return getModuleIcon(module);
        }
      }
    }

    return null;
  }

  @NotNull
  public static Icon getModuleIcon(@NotNull Module module) {
    if (ModuleSystemUtil.isHolderModule(module) || ModuleSystemUtil.isMainModule(module)) {
      return getAndroidModuleIcon(getModuleSystem(module));
    } else if (ModuleSystemUtil.isAndroidTestModule(module)) {
      return ANDROID_MODULE;
    }


    return AllIcons.Nodes.Module;
  }

  @NotNull
  public static Icon getAndroidModuleIcon(@NotNull AndroidModuleSystem androidModuleSystem) {
    return getAndroidModuleIcon(androidModuleSystem.getType());
  }

  @NotNull
  public static Icon getAndroidModuleIcon(@NotNull AndroidModuleSystem.Type androidProjectType) {
    switch (androidProjectType) {
      case TYPE_NON_ANDROID:
        return AllIcons.Nodes.Module;
      case TYPE_APP:
        return ANDROID_MODULE;
      case TYPE_FEATURE:
      case TYPE_DYNAMIC_FEATURE:
        return FEATURE_MODULE;
      case TYPE_INSTANTAPP:
        return INSTANT_APPS;
      case TYPE_LIBRARY:
        return LIBRARY_MODULE;
      case TYPE_TEST:
        return ANDROID_TEST_ROOT;
      default:
        return ANDROID_MODULE;
    }
  }
}
