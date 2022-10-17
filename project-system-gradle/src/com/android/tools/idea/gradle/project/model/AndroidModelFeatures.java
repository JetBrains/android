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
package com.android.tools.idea.gradle.project.model;

import com.android.ide.common.repository.AgpVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidModelFeatures {
  @Nullable private final AgpVersion myModelVersion;
  private final boolean myBuildOutputFileSupported;
  private final boolean myPostBuildSyncSupported;
  public AndroidModelFeatures(@Nullable AgpVersion modelVersion) {
    myModelVersion = modelVersion;
    myBuildOutputFileSupported = modelVersionIsAtLeast("4.1.0");

    if (modelVersion != null) {
      // Enable post build sync if AGP is between AGP[3.0, 4.0].
      // For AGP prior to 3.0, post build sync is not supported. For 4.1 and higher, build output listing file should be used.
      myPostBuildSyncSupported = modelVersionIsAtLeast("3.0.0") && !modelVersionIsAtLeast("4.1.0");
    }
    else {
      myPostBuildSyncSupported = false;
    }
  }

  private boolean modelVersionIsAtLeast(@NotNull String revision) {
    return myModelVersion != null && myModelVersion.compareIgnoringQualifiers(revision) >= 0;
  }

  public boolean isPostBuildSyncSupported() {
    return myPostBuildSyncSupported;
  }

  public boolean isBuildOutputFileSupported() {
    return myBuildOutputFileSupported;
  }
}
