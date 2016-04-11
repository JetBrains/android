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
package com.android.tools.idea.gradle.structure.model.repositories.search;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.builder.model.Variant;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import static com.android.ide.common.repository.GradleCoordinate.parseCoordinateString;
import static com.android.tools.idea.templates.RepositoryUrlManager.*;

public class AndroidSdkRepository extends ArtifactRepository {
  private final List<GradleCoordinate> myGradleCoordinates = Lists.newArrayList();

  public AndroidSdkRepository(@Nullable AndroidProject androidProject) {
    this(androidProject, IdeSdks.getAndroidSdkPath());
  }

  @VisibleForTesting
  AndroidSdkRepository(@Nullable AndroidProject androidProject, @Nullable File androidSdkPath) {
    if (androidSdkPath != null) {
      boolean preview = false;
      if (androidProject != null) {
        preview = includePreview(androidProject);
      }
      for (String libraryId : EXTRAS_REPOSITORY.keySet()) {
        GradleCoordinate coordinate = getLibraryCoordinate(libraryId, androidSdkPath, preview);
        if (coordinate != null) {
          myGradleCoordinates.add(coordinate);
        }
      }
    }
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

  @Override
  @NotNull
  public String getName() {
    return "Android SDK";
  }

  @Override
  @NotNull
  protected SearchResult doSearch(@NotNull SearchRequest request) {
    List<String> data = Lists.newArrayList();
    for (GradleCoordinate gradleCoordinate : myGradleCoordinates) {
      if (!matches(gradleCoordinate.getGroupId(), request.getGroupId())) {
        continue;
      }
      if (matches(gradleCoordinate.getArtifactId(), request.getArtifactName())) {
        data.add(gradleCoordinate.toString());
      }
    }
    return new SearchResult(getName(), data, data.size());
  }

  private static boolean matches(@Nullable String s1, @Nullable String s2) {
    if (Objects.equal(s1, s2)) {
      return true;
    }
    if (s2 == null) {
      return true;
    }
    return s1 != null && s1.contains(s2);
  }
}
