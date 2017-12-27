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

import com.intellij.jarFinder.InternetAttachSourceProvider;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.openapi.util.io.FileUtil.notNullize;

public class LibraryFilePaths {
  @NotNull
  public static LibraryFilePaths getInstance() {
    return ServiceManager.getService(LibraryFilePaths.class);
  }

  @Nullable
  public File findSourceJarPath(@NotNull File libraryPath) {
    return findArtifactFilePathInRepository(libraryPath, "-sources.jar", true);
  }

  @Nullable
  public File findJavadocJarPath(@NotNull File libraryPath) {
    return findArtifactFilePathInRepository(libraryPath, "-javadoc.jar", true);
  }

  @Nullable
  public File findPomPathForLibrary(@NotNull File libraryPath) {
    return findArtifactFilePathInRepository(libraryPath, ".pom", false);
  }

  @Nullable
  private static File findArtifactFilePathInRepository(@NotNull File libraryPath,
                                                       @NotNull String fileNameSuffix,
                                                       boolean searchInIdeCache) {
    if (!libraryPath.isFile()) {
      // Unlikely to happen. At this point the jar file should exist.
      return null;
    }

    File parentPath = libraryPath.getParentFile();
    String name = getNameWithoutExtension(libraryPath);
    String sourceFileName = name + fileNameSuffix;
    if (parentPath != null) {

      // Try finding sources in the same folder as the jar file. This is the layout of Maven repositories.
      File sourceJar = findChildPath(parentPath, sourceFileName);
      if (sourceJar != null) {
        return sourceJar;
      }

      // Try the parent's parent. This is the layout of the repository cache in .gradle folder.
      parentPath = parentPath.getParentFile();
      if (parentPath != null) {
        for (File child : notNullize(parentPath.listFiles())) {
          if (child.isDirectory()) {
            sourceJar = findChildPath(child, sourceFileName);
            if (sourceJar != null) {
              return sourceJar;
            }
          }
        }
      }
    }

    if (searchInIdeCache) {
      // Try IDEA's own cache.
      File librarySourceDirPath = InternetAttachSourceProvider.getLibrarySourceDir();
      File sourceJarPath = new File(librarySourceDirPath, sourceFileName);
      if (sourceJarPath.isFile()) {
        return sourceJarPath;
      }
    }
    return null;
  }

  @Nullable
  private static File findChildPath(@NotNull File parentPath, @NotNull String childName) {
    for (File child : notNullize(parentPath.listFiles())) {
      if (childName.equals(child.getName())) {
        return child.isFile() ? child : null;
      }
    }
    return null;
  }
}
