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
package com.android.tools.idea.gradle;

import com.android.annotations.Nullable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import org.jetbrains.annotations.NotNull;

public class ProjectLibraries {
  @NotNull private final Project myProject;

  public ProjectLibraries(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  public Library findMatchingLibrary(@NotNull String nameRegEx) {
    LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    for (Library library : table.getLibraries()) {
      String name = library.getName();
      if (name != null && name.matches(nameRegEx)) {
        return library;
      }
    }
    return null;
  }
}
