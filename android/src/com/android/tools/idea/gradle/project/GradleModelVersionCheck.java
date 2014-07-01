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

import com.android.SdkConstants;
import com.android.builder.model.AndroidProject;
import com.android.sdklib.repository.FullRevision;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class GradleModelVersionCheck {
  private static final Logger LOG = Logger.getInstance(GradleModelVersionCheck.class);

  static final FullRevision MINIMUM_SUPPORTED_VERSION = FullRevision.parseRevision(SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION);

  static boolean isSupportedVersion(@NotNull AndroidProject androidProject) {
    return isSupportedVersion(androidProject, MINIMUM_SUPPORTED_VERSION);
  }

  static boolean isSupportedVersion(@NotNull AndroidProject androidProject, @NotNull FullRevision minimumSupportedVersion) {
    FullRevision version = getModelVersion(androidProject);
    if (version != null) {
      return version.compareTo(minimumSupportedVersion) >= 0;
    }
    return false;
  }

  @Nullable
  static FullRevision getModelVersion(@NotNull AndroidProject androidProject) {
    String modelVersion = androidProject.getModelVersion();
    if (Strings.isNullOrEmpty(modelVersion)) {
      return null;
    }
    int snapshotIndex = modelVersion.indexOf("-");
    if (snapshotIndex != -1) {
      modelVersion = modelVersion.substring(0, snapshotIndex);
    }
    try {
      return FullRevision.parseRevision(modelVersion);
    } catch (NumberFormatException e) {
      LOG.info(String.format("Unable to parse Gradle model version '%1$s'", modelVersion), e);
      return null;
    }
  }

  private GradleModelVersionCheck() {
  }
}
