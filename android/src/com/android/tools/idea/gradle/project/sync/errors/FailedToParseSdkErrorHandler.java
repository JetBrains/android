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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.tools.idea.sdk.AndroidSdks;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.android.SdkConstants.FD_ADDONS;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.FAILED_TO_PARSE_SDK;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.SystemProperties.getUserName;

public class FailedToParseSdkErrorHandler extends BaseSyncErrorHandler {
  @Override
  @Nullable
  protected String findErrorMessage(@NotNull Throwable rootCause, @NotNull Project project) {
    String text = rootCause.getMessage();
    if (rootCause instanceof RuntimeException && isNotEmpty(text) && text.contains("failed to parse SDK")) {
      text += EMPTY_LINE + "The Android SDK may be missing the directory 'add-ons'.";
      File pathOfBrokenSdk = AndroidSdks.getInstance().findPathOfSdkWithoutAddonsFolder(project);
      String newMsg;
      if (pathOfBrokenSdk != null) {
        newMsg = String.format("The directory '%1$s', in the Android SDK at '%2$s', is either missing or empty", FD_ADDONS,
                               pathOfBrokenSdk.getPath());
        if (!pathOfBrokenSdk.canWrite()) {
          String format = "\n\nCurrent user (%1$s) does not have write access to the SDK directory.";
          newMsg += String.format(format, getUserName());
        }
      }
      else {
        newMsg = getFirstLineMessage(text);
      }
      updateUsageTracker(FAILED_TO_PARSE_SDK);
      return newMsg;
    }
    return null;
  }
}