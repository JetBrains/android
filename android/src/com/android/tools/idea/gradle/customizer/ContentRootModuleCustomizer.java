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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Sets the content roots of an IDEA module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class ContentRootModuleCustomizer implements ModuleCustomizer {
  /**
   * Sets the content roots of the given IDEA module based on the settings of the given Android-Gradle project.
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
    } finally {
      model.commit();
    }
  }

  @Nullable
  private static ContentEntry findMatchingContentEntry(@NotNull ModifiableRootModel model, @NotNull IdeaAndroidProject ideaAndroidProject) {
    ContentEntry[] contentEntries = model.getContentEntries();
    if (contentEntries == null || contentEntries.length == 0) {
      return null;
    }
    File rootDir = new File(ideaAndroidProject.getRootDirPath());
    for (ContentEntry contentEntry : contentEntries) {
      VirtualFile contentEntryFile = contentEntry.getFile();
      if (contentEntryFile == null) {
        continue;
      }
      File contentEntryRoot = new File(contentEntryFile.getPath());
      if (FileUtil.filesEqual(rootDir, contentEntryRoot)) {
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
        boolean isTestSource = sourceType.equals(ExternalSystemSourceType.TEST);
        String url = VfsUtilCore.pathToUrl(dir.getAbsolutePath());
        contentEntry.addSourceFolder(url, isTestSource);
      }
    };
    AndroidContentRoot.storePaths(ideaAndroidProject, storage);
  }
}
