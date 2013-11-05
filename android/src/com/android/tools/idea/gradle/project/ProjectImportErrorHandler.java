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

import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler;

/**
 * Provides better error messages for project import failures.
 */
public class ProjectImportErrorHandler extends AbstractProjectImportErrorHandler {
  public static final String FAILED_TO_PARSE_SDK = "failed to parse SDK";
  public static final String INSTALL_ANDROID_SUPPORT_REPO = "Please install the Android Support Repository from the Android SDK Manager.";
  private static final Logger LOG = Logger.getInstance(ProjectImportErrorHandler.class);

  private static final String EMPTY_LINE = "\n\n";
  private static final String UNSUPPORTED_GRADLE_VERSION_ERROR =
    "Gradle version " + GradleUtil.GRADLE_MINIMUM_VERSION + " is required";

  @Nullable
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    if (error instanceof ExternalSystemException) {
      // This is already a user-friendly error.
      return (ExternalSystemException)error;
    }

    Pair<Throwable, String> rootCauseAndLocation = getRootCauseAndLocation(error);
    Throwable rootCause = rootCauseAndLocation.getFirst();

    if (rootCause instanceof RuntimeException) {
      String msg = rootCause.getMessage();
      // With this condition we cover 2 similar messages about the same problem.
      if (msg != null && msg.contains("Could not find") && msg.contains("com.android.support:support")) {
        // We keep the original error message and we append a hint about how to fix the missing dependency.
        String newMsg = msg + EMPTY_LINE + INSTALL_ANDROID_SUPPORT_REPO;
        // Location of build.gradle is useless for this error. Omitting it.
        return createUserFriendlyError(newMsg, null);
      }

      if (msg != null && msg.contains(FAILED_TO_PARSE_SDK)) {
        String newMsg = msg + EMPTY_LINE + "The Android SDK may be missing the directory 'add-ons'.";
        // Location of build.gradle is useless for this error. Omitting it.
        return createUserFriendlyError(newMsg, null);
      }
    }

    return null;
  }
}
