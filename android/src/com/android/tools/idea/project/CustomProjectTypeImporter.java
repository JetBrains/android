/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.project;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Imports a file and creates a project of a custom type from it.
 */
public abstract class CustomProjectTypeImporter {
  private static final ExtensionPointName<CustomProjectTypeImporter> EP_NAME =
    ExtensionPointName.create("com.android.customProjectTypeImporter");

  @NotNull
  public static MainImporter getMain() {
    return ServiceManager.getService(MainImporter.class);
  }

  /**
   * Indicates whether this importer supports the type of the given file.
   *
   * @param file the given file.
   * @return {@code true} if this importer supports the type of the given file; {@code false} otherwise.
   */
  public abstract boolean canImport(@NotNull VirtualFile file);

  /**
   * Imports the given file and creates a new project based on the type and contents of the given file.
   *
   * @param file the given file.
   */
  public abstract void importFile(@NotNull VirtualFile file);

  public static class MainImporter {
    @NotNull private final CustomProjectTypeImporter[] myExtensions;

    @SuppressWarnings("unused") // Instantiated by IDEA
    public MainImporter() {
      this(EP_NAME.getExtensions());
    }

    @VisibleForTesting
    MainImporter(@NotNull CustomProjectTypeImporter... extensions) {
      myExtensions = extensions;
    }

    /**
     * Attempts to import the given file and create a new project based on the type and contents of the given file.
     *
     * @param file the given file.
     * @return {@code true} if the file was recognized and imported; {@code false} otherwise.
     */
    public boolean importFileAsProject(@NotNull VirtualFile file) {
      for (CustomProjectTypeImporter importer : myExtensions) {
        if (importer.canImport(file)) {
          importer.importFile(file);
          return true;
        }
      }
      return false;
    }
  }
}
