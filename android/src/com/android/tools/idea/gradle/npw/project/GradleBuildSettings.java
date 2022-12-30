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
package com.android.tools.idea.gradle.npw.project;

import static com.android.SdkConstants.CURRENT_BUILD_TOOLS_VERSION;
import static com.android.repository.Revision.parseRevision;

import com.android.repository.Revision;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.BuildToolInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import org.jetbrains.annotations.NotNull;

public final class GradleBuildSettings {
  /**
   * Gets the recommended gradle tools {@link Revision}. If there is no "stable" installed version, it will use the latest installed
   * "preview" version.
   * If no stable or preview version is installed, or they are to older than the recommend version, it returns last known recommend version
   *
   * @param sdkHandler The installed Android SDK handler
   * @param progress a progress indicator
   */
  @NotNull
  public static Revision getRecommendedBuildToolsRevision(@NotNull AndroidSdkHandler sdkHandler, @NotNull ProgressIndicator progress) {
    BuildToolInfo buildTool = sdkHandler.getLatestBuildTool(progress, false);
    if (buildTool == null) {
      buildTool = sdkHandler.getLatestBuildTool(progress, true);
    }
    Revision minimumBuildToolsRev = parseRevision(CURRENT_BUILD_TOOLS_VERSION);
    boolean isOld = (buildTool == null || buildTool.getRevision().compareTo(minimumBuildToolsRev) < 0);

    return isOld ? minimumBuildToolsRev : buildTool.getRevision();
  }
}
