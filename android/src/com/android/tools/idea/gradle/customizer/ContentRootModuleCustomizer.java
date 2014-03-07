/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer;

import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.project.AndroidContentRoot;
import com.android.tools.idea.gradle.project.AndroidContentRoot.ContentRootStorage;
import com.google.common.base.Preconditions;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;

/**
 * Sets the content roots of an IDEA module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class ContentRootModuleCustomizer implements ModuleCustomizer {
  /**
   * Sets the content roots of the given IDEA module based on the settings of the given Android-Gradle project.
   *
   * @param module             module to customize.
   * @param project            project that owns the module to customize.
   * @param ideaAndroidProject the imported Android-Gradle project.
   */
  @Override
  public void customizeModule(@NotNull Module module, @NotNull Project project, @Nullable IdeaAndroidProject ideaAndroidProject) {
    if (ideaAndroidProject == null) {
      return;
    }
    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    ModifiableRootModel model = moduleRootManager.getModifiableModel();

    final ContentEntry contentEntry = findMatchingContentEntry(model, ideaAndroidProject);
    if (contentEntry == null) {
      model.dispose();
      return;
    }
    try {
      contentEntry.clearSourceFolders();
      storePaths(contentEntry, ideaAndroidProject);
    }
    finally {
      model.commit();
    }
  }

  @Nullable
  private static ContentEntry findMatchingContentEntry(@NotNull ModifiableRootModel model, @NotNull IdeaAndroidProject ideaAndroidProject) {
    ContentEntry[] contentEntries = model.getContentEntries();
    if (contentEntries.length == 0) {
      return null;
    }
    VirtualFile rootDir = ideaAndroidProject.getRootDir();
    for (ContentEntry contentEntry : contentEntries) {
      VirtualFile contentEntryFile = contentEntry.getFile();
      if (rootDir.equals(contentEntryFile)) {
        return contentEntry;
      }
    }
    return null;
  }

  private static void storePaths(@NotNull final ContentEntry contentEntry, @NotNull IdeaAndroidProject ideaAndroidProject) {
    ContentRootStorage storage = new ContentRootStorage() {
      @Override
      @NotNull
      public String getRootDirPath() {
        VirtualFile file = Preconditions.checkNotNull(contentEntry.getFile());
        return file.getPath();
      }

      @Override
      public void storePath(@NotNull ExternalSystemSourceType sourceType, @NotNull File dir) {
        if (sourceType.equals(ExternalSystemSourceType.EXCLUDED)) {
          return;
        }

        String dirPath = FileUtil.toSystemIndependentName(dir.getPath());
        String url = VfsUtilCore.pathToUrl(dirPath);
        if (sourceType.equals(ExternalSystemSourceType.SOURCE)) {
          storePath(url, JavaSourceRootType.SOURCE, false);
        }
        else if (sourceType.equals(ExternalSystemSourceType.SOURCE_GENERATED)) {
          storePath(url, JavaSourceRootType.SOURCE, true);
        }
        else if (sourceType.equals(ExternalSystemSourceType.RESOURCE)) {
          storePath(url, JavaResourceRootType.RESOURCE, false);
        }
        else if (sourceType.equals(ExternalSystemSourceType.TEST)) {
          storePath(url, JavaSourceRootType.TEST_SOURCE, false);
        }
        else if (sourceType.equals(ExternalSystemSourceType.TEST_GENERATED)) {
          storePath(url, JavaSourceRootType.TEST_SOURCE, true);
        }
        else if (sourceType.equals(ExternalSystemSourceType.TEST_RESOURCE)) {
          storePath(url, JavaResourceRootType.TEST_RESOURCE, false);
        }
      }

      private void storePath(@NotNull String url, @NotNull JpsModuleSourceRootType sourceRootType, boolean isGenerated) {
        SourceFolder sourceFolder = contentEntry.addSourceFolder(url, sourceRootType);

        if (isGenerated) {
          JpsModuleSourceRoot sourceRoot = sourceFolder.getJpsElement();
          JpsElement properties = sourceRoot.getProperties();
          if (properties instanceof JavaSourceRootProperties) {
            ((JavaSourceRootProperties)properties).setForGeneratedSources(true);
          }
        }
      }
    };
    AndroidContentRoot.storePaths(ideaAndroidProject, storage);
  }
}
