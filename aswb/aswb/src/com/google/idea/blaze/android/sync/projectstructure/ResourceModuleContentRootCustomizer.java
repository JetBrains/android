/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync.projectstructure;

import com.google.idea.blaze.android.projectsystem.BlazeSampleDataDirectoryProvider;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.URLUtil;
import java.io.File;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;

class ResourceModuleContentRootCustomizer {

  public static void setupContentRoots(
      @NotNull ModifiableRootModel model, @NotNull Collection<File> resources) {
    for (ContentEntry contentEntry : model.getContentEntries()) {
      model.removeContentEntry(contentEntry);
    }

    for (File resource : resources) {
      ContentEntry contentEntry = model.addContentEntry(pathToUrl(resource.getPath()));
      SourceFolder sourceFolder =
          contentEntry.addSourceFolder(
              pathToUrl(resource.getPath()), JavaResourceRootType.RESOURCE);

      setupSampleDataContentRoot(model, sourceFolder);
    }
  }

  private static void setupSampleDataContentRoot(
      @NotNull ModifiableRootModel model, @NotNull SourceFolder resSourceFolder) {
    VirtualFile resFolder = resSourceFolder.getFile();
    if (resFolder == null) {
      return;
    }

    VirtualFile sampleDataDirectory =
        BlazeSampleDataDirectoryProvider.getSampleDataDirectoryForResFolder(resFolder);
    if (sampleDataDirectory != null && sampleDataDirectory.exists()) {
      model.addContentEntry(sampleDataDirectory);
    }
  }

  @NotNull
  private static String pathToUrl(@NotNull String filePath) {
    filePath = FileUtil.toSystemIndependentName(filePath);
    if (filePath.endsWith(".srcjar") || filePath.endsWith(".jar")) {
      return URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + filePath + URLUtil.JAR_SEPARATOR;
    } else if (filePath.contains("src.jar!")) {
      return URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + filePath;
    } else {
      return VfsUtilCore.pathToUrl(filePath);
    }
  }
}
