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
import com.android.tools.idea.projectsystem.AndroidIconProviderProjectToken;
import com.android.tools.idea.projectsystem.AndroidProjectSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.FlagManager;
import com.intellij.ide.IconProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.SdkConstants.FN_ANDROID_MANIFEST_XML;

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

    Project project = element.getProject();
    AndroidProjectSystem projectSystem = ProjectSystemUtil.getProjectSystem(project);
    AndroidIconProviderProjectToken<AndroidProjectSystem> token =
      AndroidIconProviderProjectToken.getEP_NAME().getExtensionList().stream()
        .filter(it -> it.isApplicable(projectSystem))
        .findFirst().orElse(null);
    if (token != null) {
      return token.getIcon(projectSystem, element);
    }
    return null;
  }
}
