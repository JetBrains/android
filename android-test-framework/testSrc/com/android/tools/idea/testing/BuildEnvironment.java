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
package com.android.tools.idea.testing;

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.gradle.plugin.AgpVersions;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.CURRENT_BUILD_TOOLS_VERSION;

public class BuildEnvironment {

  private static BuildEnvironment ourInstance;

  @NotNull private final String myGradlePluginVersion;
  @NotNull private final String myBuildToolsVersion;
  private final int myCompileSdkVersion;
  private final int myTargetSdkVersion;
  private final int myMinSdkVersion;

  private BuildEnvironment(@NotNull String gradlePluginVersion,
                           @NotNull String buildToolsVersion,
                           int compileSdkVersion,
                           int targetSdkVersion,
                           int minSdkVersion) {
    myGradlePluginVersion = gradlePluginVersion;
    myBuildToolsVersion = buildToolsVersion;
    myCompileSdkVersion = compileSdkVersion;
    myTargetSdkVersion = targetSdkVersion;
    myMinSdkVersion = minSdkVersion;
  }

  @NotNull
  public synchronized static BuildEnvironment getInstance() {
    if (ourInstance == null) {
      ourInstance = new BuildEnvironment(
        AgpVersions.getLatestKnown().toString(),
        CURRENT_BUILD_TOOLS_VERSION,
        SdkVersionInfo.HIGHEST_KNOWN_STABLE_API,
        SdkVersionInfo.HIGHEST_KNOWN_STABLE_API,
        SdkVersionInfo.LOWEST_ACTIVE_API
      );
    }
    return ourInstance;
  }

  @NotNull
  public String getGradlePluginVersion() {
    return myGradlePluginVersion;
  }

  @NotNull
  public String getBuildToolsVersion() {
    return myBuildToolsVersion;
  }

  public String getCompileSdkVersion() {
    return String.valueOf(myCompileSdkVersion);
  }

  public String getTargetSdkVersion() {
    return String.valueOf(myTargetSdkVersion);
  }

  public String getMinSdkVersion() {
    return String.valueOf(myMinSdkVersion);
  }
}
