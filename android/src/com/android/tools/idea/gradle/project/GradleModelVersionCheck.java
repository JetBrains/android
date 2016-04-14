/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.builder.model.AndroidProject;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION;
import static com.google.common.base.Strings.isNullOrEmpty;

final class GradleModelVersionCheck {
  static final GradleVersion MINIMUM_SUPPORTED_VERSION = GradleVersion.parse(GRADLE_PLUGIN_MINIMUM_VERSION);

  static boolean isSupportedVersion(@NotNull AndroidProject androidProject) {
    return isSupportedVersion(androidProject, MINIMUM_SUPPORTED_VERSION);
  }

  static boolean isSupportedVersion(@NotNull AndroidProject androidProject, @NotNull GradleVersion minimumSupportedVersion) {
    GradleVersion version = getModelVersion(androidProject);
    if (version != null) {
      return version.compareTo(minimumSupportedVersion) >= 0;
    }
    return false;
  }

  @Nullable
  static GradleVersion getModelVersion(@NotNull AndroidProject androidProject) {
    String modelVersion = androidProject.getModelVersion();
    if (isNullOrEmpty(modelVersion)) {
      return null;
    }
    int snapshotIndex = modelVersion.indexOf("-");
    if (snapshotIndex != -1) {
      modelVersion = modelVersion.substring(0, snapshotIndex);
    }
    return GradleVersion.tryParse(modelVersion);
  }

  private GradleModelVersionCheck() {
  }
}
