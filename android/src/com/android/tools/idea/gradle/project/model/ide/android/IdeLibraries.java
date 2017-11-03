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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import com.android.tools.idea.gradle.project.model.ide.android.level2.BuildFolderPaths;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.utils.FileUtils.isFileInDirectory;
import static com.intellij.openapi.util.text.StringUtil.trimStart;

public final class IdeLibraries {
  private IdeLibraries() {
  }

  /**
   * @param library Instance of level 1 Library.
   * @return The artifact address that can be used as unique identifier in global library map.
   */
  @NotNull
  public static String computeAddress(@NotNull Library library) {
    // If the library is an android module dependency, use projectPath::variant as unique identifier.
    // MavenCoordinates cannot be used because it doesn't contain variant information, which results
    // in the same MavenCoordinates for different variants of the same module.
    try {
      if (library.getProject() != null && library instanceof AndroidLibrary) {
        return library.getProject() + "::" + ((AndroidLibrary)library).getProjectVariant();
      }
    }
    catch (UnsupportedOperationException ex) {
      // getProject() isn't available for pre-2.0 plugins. Proceed with MavenCoordinates.
      // Anyway pre-2.0 plugins don't have variant information for module dependency.
    }
    MavenCoordinates coordinate = computeResolvedCoordinate(library, new ModelCache());
    String artifactId = trimStart(coordinate.getArtifactId(), ":");
    artifactId = artifactId.replace(':', '.');
    String address = coordinate.getGroupId() + ":" + artifactId + ":" + coordinate.getVersion();
    String classifier = coordinate.getClassifier();
    if (classifier != null) {
      address = address + ":" + classifier;
    }
    String packaging = coordinate.getPackaging();
    address = address + "@" + packaging;
    return address.intern();
  }

  /**
   * Indicates whether the given library is a module wrapping an AAR file.
   */
  public static boolean isLocalAarModule(@NotNull AndroidLibrary androidLibrary, @NotNull BuildFolderPaths buildFolderPaths) {
    String projectPath = androidLibrary.getProject();
    if (projectPath == null) {
      return false;
    }
    File buildFolderPath = buildFolderPaths.findBuildFolderPath(projectPath);
    // If the aar bundle is inside of build directory, then it's a regular library module dependency, otherwise it's a wrapped aar module.
    return buildFolderPath != null && !isFileInDirectory(androidLibrary.getBundle(), buildFolderPath);
  }

  @NotNull
  public static IdeMavenCoordinates computeResolvedCoordinate(@NotNull Library library, @NotNull ModelCache modelCache) {
    // Although getResolvedCoordinates is annotated with @NotNull, it can return null for plugin 1.5,
    // when the library dependency is from local jar.
    //noinspection ConstantConditions
    if (library.getResolvedCoordinates() != null) {
      return modelCache.computeIfAbsent(library.getResolvedCoordinates(),
                                        coordinates -> new IdeMavenCoordinates(coordinates, modelCache));
    }
    else {
      File jarFile;
      if (library instanceof JavaLibrary) {
        jarFile = ((JavaLibrary)library).getJarFile();
      }
      else {
        jarFile = ((AndroidLibrary)library).getBundle();
      }
      return new IdeMavenCoordinates(jarFile, new ModelCache());
    }
  }
}
