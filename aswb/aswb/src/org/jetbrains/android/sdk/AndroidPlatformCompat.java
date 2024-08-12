/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.sdk;

import com.android.sdklib.BuildToolInfo;
import com.android.tools.sdk.AndroidPlatform;
import com.intellij.openapi.projectRoots.Sdk;

/** Compat class for {@link com.android.tools.sdk.AndroidPlatform} */
public class AndroidPlatformCompat {
  AndroidPlatform androidPlatform;

  public AndroidPlatformCompat(AndroidPlatform androidPlatform) {
    this.androidPlatform = androidPlatform;
  }

  public BuildToolInfo getLatestBuildTool(boolean allowBuildTool) {
    return androidPlatform.getSdkData().getLatestBuildTool(allowBuildTool);
  }

  public int getApiLevel() {
    return androidPlatform.getApiLevel();
  }

  public static int getApiLevel(Sdk sdk) {
    int androidSdkApiLevel = 1;
    AndroidSdkAdditionalData additionalData = (AndroidSdkAdditionalData) sdk.getSdkAdditionalData();
    if (additionalData != null) {
      AndroidPlatform androidPlatform = additionalData.getAndroidPlatform();
      if (androidPlatform != null) {
        androidSdkApiLevel = androidPlatform.getApiLevel();
      }
    }
    return androidSdkApiLevel;
  }
}
