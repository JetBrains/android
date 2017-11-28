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
package com.android.tools.idea.gradle.project.sync.precheck;

import com.android.tools.idea.gradle.project.sync.SdkSync;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult.SUCCESS;
import static com.android.tools.idea.gradle.project.sync.precheck.PreSyncCheckResult.failure;

class AndroidSdkPreSyncCheck extends AndroidStudioSyncCheck {
  @NotNull private final SdkSync mySdkSync;

  AndroidSdkPreSyncCheck() {
    this(SdkSync.getInstance());
  }

  @VisibleForTesting
  AndroidSdkPreSyncCheck(@NotNull SdkSync sdkSync) {
    mySdkSync = sdkSync;
  }

  @NotNull
  @Override
  PreSyncCheckResult canSync(@NotNull Project project) {
    return doCheckCanSync(project);
  }

  @Override
  @NotNull
  PreSyncCheckResult doCheckCanSync(@NotNull Project project) {
    try {
      mySdkSync.syncIdeAndProjectAndroidSdks(project);
      return SUCCESS;
    }
    catch (Throwable e) {
      String msg = "Failed to sync SDKs";
      return failure(msg);
    }
  }
}
