/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.Variant;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.gradle.structure.configurables.model.ModuleMergedModel;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.android.ide.common.repository.GradleCoordinate.parseCoordinateString;
import static com.android.tools.idea.templates.RepositoryUrlManager.*;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

class AndroidSdkRepositorySearch extends ArtifactRepositorySearch {
  private final List<GradleCoordinate> myGradleCoordinates = Lists.newArrayList();

  AndroidSdkRepositorySearch(@NotNull ModuleMergedModel model) {
    this(model, IdeSdks.getAndroidSdkPath());
  }

  @VisibleForTesting
  AndroidSdkRepositorySearch(@NotNull ModuleMergedModel model, @Nullable File androidSdkPath) {
    if (androidSdkPath != null) {
      AndroidProject androidProject = model.getAndroidProject();
      boolean preview = includePreview(androidProject);
      for (String libraryId : EXTRAS_REPOSITORY.keySet()) {
        GradleCoordinate coordinate = getLibraryCoordinate(libraryId, androidSdkPath, preview);
        if (coordinate != null) {
          myGradleCoordinates.add(coordinate);
        }
      }
    }
  }

  @VisibleForTesting
  @Nullable
  static GradleCoordinate getLibraryCoordinate(@NotNull String libraryId, @NotNull File androidSdkPath, boolean preview) {
    RepositoryUrlManager.RepositoryLibrary library = EXTRAS_REPOSITORY.get(libraryId);
    File metadataFile = new File(String.format(library.basePath, androidSdkPath, library.id), MAVEN_METADATA_FILE_NAME);
    String coordinateText = null;
    if (!metadataFile.exists()) {
      coordinateText = String.format(library.baseCoordinate, library.id, REVISION_ANY);
    }
    else {
      RepositoryUrlManager urlManager = RepositoryUrlManager.get();
      String version = urlManager.getLatestVersionFromMavenMetadata(metadataFile, null, preview);
      if (version != null) {
        coordinateText = String.format(library.baseCoordinate, library.id, version);
      }
    }
    return coordinateText != null ? parseCoordinateString(coordinateText) : null;
  }

  private static boolean includePreview(@NotNull AndroidProject androidProject) {
    for (Variant variant : androidProject.getVariants()) {
      ApiVersion minSdkVersion = variant.getMergedFlavor().getMinSdkVersion();
      if (minSdkVersion != null) {
        boolean preview = new AndroidVersion(minSdkVersion.getApiLevel(), minSdkVersion.getCodename()).isPreview();
        if (preview) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  @NotNull
  String getName() {
    return "Android SDK";
  }

  @Override
  boolean supportsPagination() {
    return false;
  }

  @Override
  @NotNull
  SearchResult start(@NotNull Request request) throws IOException {
    List<String> data = Lists.newArrayList();
    for (GradleCoordinate gradleCoordinate : myGradleCoordinates) {
      String groupId = request.groupId;
      if (isNotEmpty(groupId)) {
        if (!groupId.equals(gradleCoordinate.getGroupId())) {
          continue;
        }
      }
      if (request.artifactName.equals(gradleCoordinate.getArtifactId())) {
        data.add(gradleCoordinate.toString());
      }
    }

    return new SearchResult(getName(), data, data.size());
  }
}
