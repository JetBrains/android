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
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.roots.OrderRootType.CLASSES;

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
  }
}
