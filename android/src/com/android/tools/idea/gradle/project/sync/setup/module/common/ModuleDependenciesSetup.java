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
package com.android.tools.idea.gradle.project.sync.setup.module.common;

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;

public abstract class ModuleDependenciesSetup {
  protected void updateLibraryRootTypePaths(@NotNull Library library,
                                            @NotNull OrderRootType pathType,
                                            @NotNull IdeModifiableModelsProvider modelsProvider,
                                            @NotNull File... paths) {
    if (paths.length == 0) {
      return;
    }
    // We only update paths if the library does not have any already defined.
    Library.ModifiableModel libraryModel = modelsProvider.getModifiableLibraryModel(library);
    for (File path : paths) {
      libraryModel.addRoot(pathToIdeaUrl(path), pathType);
    }
  }

  protected void addLibraryAsDependency(@NotNull Library library,
                                        @NotNull String libraryName,
                                        @NotNull DependencyScope scope,
                                        @NotNull Module module,
                                        @NotNull IdeModifiableModelsProvider modelsProvider,
                                        boolean exported) {
    for (OrderEntry orderEntry : modelsProvider.getModifiableRootModel(module).getOrderEntries()) {
      if (orderEntry instanceof LibraryOrderEntry) {
        Library entryLibrary = ((LibraryOrderEntry)orderEntry).getLibrary();
        if (entryLibrary != null && libraryName.equals(entryLibrary.getName())) {
          // Dependency already set up.
          return;
        }
      }
    }

    LibraryOrderEntry orderEntry = modelsProvider.getModifiableRootModel(module).addLibraryEntry(library);
    orderEntry.setScope(scope);
    orderEntry.setExported(exported);
  }
}
