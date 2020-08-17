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
package com.android.tools.idea.gradle.project.sync.idea;

import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import static com.android.SdkConstants.GRADLE_PLUGIN_MINIMUM_VERSION;
import static com.google.common.base.Strings.isNullOrEmpty;

final class GradleModelVersionCheck {
  static final GradleVersion MINIMUM_SUPPORTED_VERSION = GradleVersion.parse(GRADLE_PLUGIN_MINIMUM_VERSION);

  @TestOnly
  static boolean isSupportedVersion(@Nullable String modelVersion) {
    GradleVersion version = getModelVersion(modelVersion);
    return isSupportedVersion(version);
  }

  static boolean isSupportedVersion(@Nullable GradleVersion modelVersion) {
    if (modelVersion != null) {
      return modelVersion.compareTo(MINIMUM_SUPPORTED_VERSION) >= 0;
    }
    return false;
  }

  @Nullable
  static GradleVersion getModelVersion(@Nullable String modelVersion) {
    if (isNullOrEmpty(modelVersion)) {
      return null;
    }
    int snapshotIndex = modelVersion.indexOf('-');
    if (snapshotIndex != -1) {
      modelVersion = modelVersion.substring(0, snapshotIndex);
    }
    return GradleVersion.tryParse(modelVersion);
  }

  private GradleModelVersionCheck() {
  }
}
