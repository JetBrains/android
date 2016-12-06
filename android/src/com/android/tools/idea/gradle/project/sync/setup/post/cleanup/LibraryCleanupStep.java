/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post.cleanup;

import com.android.tools.idea.gradle.project.sync.setup.module.SyncLibraryRegistry;
import com.android.tools.idea.gradle.project.sync.setup.post.ProjectCleanupStep;
import com.google.common.collect.Sets;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Set;

import static com.android.tools.idea.gradle.util.FilePaths.getJarFromJarUrl;
import static com.android.tools.idea.gradle.util.FilePaths.pathToUrl;
import static com.android.tools.idea.gradle.util.GradleUtil.findSourceJarForLibrary;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;

public class LibraryCleanupStep extends ProjectCleanupStep {
  @Override
  public void cleanUpProject(@NotNull Project project,
                             @NotNull IdeModifiableModelsProvider ideModelsProvider,
                             @Nullable ProgressIndicator indicator) {
    SyncLibraryRegistry libraryRegistry = SyncLibraryRegistry.getInstance(project);
    // Remove unused libraries.
    for (Library library : libraryRegistry.getLibrariesToRemove()) {
      ideModelsProvider.removeLibrary(library);
    }
    // Update library URLs if they changed between Gradle sync operations.
    for (SyncLibraryRegistry.LibraryToUpdate libraryToUpdate : libraryRegistry.getLibrariesToUpdate()) {
      Library library = libraryToUpdate.getLibrary();
      Library.ModifiableModel libraryModel = ideModelsProvider.getModifiableLibraryModel(library);
      for (String existingBinaryUrl : libraryModel.getUrls(CLASSES)) {
        libraryModel.removeRoot(existingBinaryUrl, CLASSES);
      }
      for (String newBinaryUrl : libraryToUpdate.getNewBinaryUrls()) {
        libraryModel.addRoot(newBinaryUrl, CLASSES);
      }
    }

    Disposer.dispose(libraryRegistry);

    attachSourcesToLibraries(ideModelsProvider);
  }

  private static void attachSourcesToLibraries(@NotNull IdeModifiableModelsProvider ideModelsProvider) {
    for (Library library : ideModelsProvider.getAllLibraries()) {
      Set<String> sourcePaths = Sets.newHashSet();

      for (VirtualFile file : library.getFiles(SOURCES)) {
        sourcePaths.add(file.getUrl());
      }

      Library.ModifiableModel libraryModel = ideModelsProvider.getModifiableLibraryModel(library);

      // Find the source attachment based on the location of the library jar file.
      for (VirtualFile classFile : library.getFiles(CLASSES)) {
        VirtualFile sourceJar = findSourceJarForJar(classFile);
        if (sourceJar != null) {
          String url = pathToUrl(sourceJar.getPath());
          if (!sourcePaths.contains(url)) {
            libraryModel.addRoot(url, SOURCES);
            sourcePaths.add(url);
          }
        }
      }
    }
  }

  @Nullable
  private static VirtualFile findSourceJarForJar(@NotNull VirtualFile jarFile) {
    // We need to get the real jar file. The one that we received is just a wrapper around a URL. Getting the parent from this file returns
    // null.
    File jarFilePath = getJarFromJarUrl(jarFile.getUrl());
    return jarFilePath != null ? findSourceJarForLibrary(jarFilePath) : null;
  }
}
