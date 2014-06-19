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
import com.android.tools.idea.gradle.util.GradleUtil;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.util.Pair;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler;

import java.io.File;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Provides better error messages for android projects import failures.
 */
public class ProjectImportErrorHandler extends AbstractProjectImportErrorHandler {
  public static final String FAILED_TO_PARSE_SDK_ERROR = "failed to parse SDK";

  public static final String INSTALL_ANDROID_SUPPORT_REPO = "Please install the Android Support Repository from the Android SDK Manager.";
  public static final String FIX_SDK_DIR_PROPERTY = "Please fix the 'sdk.dir' property in the local.properties file.";

  private static final Pattern SDK_NOT_FOUND = Pattern.compile("The SDK directory '(.*?)' does not exist.");

  private static final String EMPTY_LINE = "\n\n";
  private static final String UNSUPPORTED_GRADLE_VERSION_ERROR = "Gradle version " + GradleUtil.GRADLE_MINIMUM_VERSION + " is required";
  private static final String SDK_DIR_PROPERTY_MISSING = "No sdk.dir property defined in local.properties file.";

  @Override
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

    if (isOldGradleVersion(rootCause)) {
      String msg = String.format("You are using an unsupported version of Gradle. Please use version %1$s.",
                                 GradleUtil.GRADLE_MINIMUM_VERSION);
      msg += ('\n' + FIX_GRADLE_VERSION);
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof UnknownHostException) {
      return createUserFriendlyError(String.format("Unknown host '%1$s'.", rootCause.getMessage()), null);
    }

    if (rootCause instanceof IllegalStateException || rootCause instanceof ExternalSystemException) {
      // Missing platform in SDK now also comes as a ExternalSystemException. This may be caused by changes in IDEA's "External System
      // Import" framework.
      String msg = rootCause.getMessage();
      if (msg != null) {
        if (msg.startsWith("failed to find target android-")) {
          // Location of build.gradle is useless for this error. Omitting it.
          return createUserFriendlyError(msg, null);
        }
        if (msg.startsWith("failed to find Build Tools")) {
          // Location of build.gradle is useless for this error. Omitting it.
          return createUserFriendlyError(msg, null);
        }
      }
    }

    if (rootCause instanceof RuntimeException) {
      String msg = rootCause.getMessage();

      // With this condition we cover 2 similar messages about the same problem.
      if (msg != null && msg.contains("Could not find") && msg.contains("com.android.support:")) {
        // We keep the original error message and we append a hint about how to fix the missing dependency.
        String newMsg = msg + EMPTY_LINE + INSTALL_ANDROID_SUPPORT_REPO;
        // Location of build.gradle is useless for this error. Omitting it.
        return createUserFriendlyError(newMsg, null);
      }

      if (msg != null && msg.contains(FAILED_TO_PARSE_SDK_ERROR)) {
        String newMsg = msg + EMPTY_LINE + "The Android SDK may be missing the directory 'add-ons'.";
        // Location of build.gradle is useless for this error. Omitting it.
        return createUserFriendlyError(newMsg, null);
      }

      if (msg != null && (msg.equals(SDK_DIR_PROPERTY_MISSING) || SDK_NOT_FOUND.matcher(msg).matches())) {
        String newMsg = msg;
        File buildProperties = new File(projectPath, SdkConstants.FN_LOCAL_PROPERTIES);
        if (buildProperties.isFile()) {
          newMsg += EMPTY_LINE + FIX_SDK_DIR_PROPERTY;
        }
        return createUserFriendlyError(newMsg, null);
      }
    }

    // give others GradleProjectResolverExtensions a chance to handle this error
    return null;
  }

  private static boolean isOldGradleVersion(@NotNull Throwable error) {
    if (error instanceof UnsupportedVersionException) {
      return true;
    }
    if (error instanceof UnsupportedMethodException) {
      String msg = error.getMessage();
      if (msg != null && msg.contains("GradleProject.getBuildScript")) {
        return true;
      }
    }
    if (error instanceof ClassNotFoundException) {
      String msg = error.getMessage();
      if (msg != null && msg.contains(ToolingModelBuilderRegistry.class.getName())) {
        return true;
      }
    }
    if (error instanceof RuntimeException) {
      String msg = error.getMessage();
      if (msg != null && msg.startsWith(UNSUPPORTED_GRADLE_VERSION_ERROR)) {
        return true;
      }
    }
    return false;
  }
}
