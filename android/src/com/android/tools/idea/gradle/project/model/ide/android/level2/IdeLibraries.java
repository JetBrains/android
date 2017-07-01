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
package com.android.tools.idea.gradle.project.model.ide.android.level2;

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.MavenCoordinates;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.utils.FileUtils.isFileInDirectory;
import static com.intellij.openapi.util.text.StringUtil.trimStart;

final class IdeLibraries {
  private IdeLibraries() {
  }

  /**
   * @param library Instance of level 1 Library.
   * @return The artifact address that can be used as unique identifier in global library map.
   */
  @NotNull
  static String computeAddress(@NotNull Library library) {
    MavenCoordinates coordinate = library.getResolvedCoordinates();
    String address = coordinate.getGroupId() + ":" + trimStart(coordinate.getArtifactId(), ":") + ":" + coordinate.getVersion();
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
  static boolean isLocalAarModule(@NotNull AndroidLibrary androidLibrary, @NotNull BuildFolderPaths buildFolderPaths) {
    String projectPath = androidLibrary.getProject();
    if (projectPath == null) {
      return false;
    }
    File buildFolderPath = buildFolderPaths.findBuildFolderPath(projectPath);
    // If the aar bundle is inside of build directory, then it's a regular library module dependency, otherwise it's a wrapped aar module.
    return buildFolderPath != null && !isFileInDirectory(androidLibrary.getBundle(), buildFolderPath);
  }
}
