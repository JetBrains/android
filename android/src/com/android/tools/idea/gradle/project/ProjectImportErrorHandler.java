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
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.util.Pair;
import org.gradle.api.internal.LocationAwareException;
import org.gradle.tooling.UnsupportedVersionException;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.ConnectException;
import java.net.UnknownHostException;

/**
 * Provides better error messages for project import failures.
 */
public class ProjectImportErrorHandler {
  public static final String OPEN_GRADLE_SETTINGS = "Please fix the project's Gradle settings.";
  public static final String FAILED_TO_PARSE_SDK = "failed to parse SDK";
  public static final String INSTALL_ANDROID_SUPPORT_REPO = "Please install the Android Support Repository from the Android SDK Manager.";
  public static final String SET_UP_HTTP_PROXY = "If you are behind an HTTP proxy, please configure the proxy settings either in Android Studio or Gradle.";
  public static final String UNEXPECTED_ERROR_FILE_BUG = "This is an unexpected error. Please file a bug containing the idea.log file.";
  public static final String FIX_GRADLE_VERSION =
    "Please point to a supported Gradle version in the project's Gradle settings or in the project's Gradle wrapper (if applicable.)";

  private static final Logger LOG = Logger.getInstance(ProjectImportErrorHandler.class);

  private static final String EMPTY_LINE = "\n\n";
  private static final String UNSUPPORTED_GRADLE_VERSION_ERROR =
    "Gradle version " + GradleUtil.GRADLE_MINIMUM_VERSION + " is required";

  @NotNull
  ExternalSystemException getUserFriendlyError(@NotNull Throwable error, @NotNull File projectDir, @Nullable String buildFilePath) {
    if (error instanceof ExternalSystemException) {
      // This is already a user-friendly error.
      return (ExternalSystemException)error;
    }

    LOG.info(String.format("Failed to import Gradle project at '%1$s'", projectDir.getPath()), error);

    Pair<Throwable, String> rootCauseAndLocation = getRootCauseAndLocation(error);

    Throwable rootCause = rootCauseAndLocation.getFirst();

    String location = rootCauseAndLocation.getSecond();
    if (location == null && !Strings.isNullOrEmpty(buildFilePath)) {
      location = String.format("Build file: '%1$s'", buildFilePath);
    }

    if (rootCause instanceof UnsupportedVersionException || isOldGradleVersion(rootCause)) {
      String msg = String.format("You are using an old, unsupported version of Gradle. Please use version %1$s or greater.",
                                 GradleUtil.GRADLE_MINIMUM_VERSION);
      msg += ('\n' + FIX_GRADLE_VERSION);
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof OutOfMemoryError) {
      // The OutOfMemoryError happens in the Gradle daemon process.
      String originalMessage = rootCause.getMessage();
      String msg = "Out of memory";
      if (originalMessage != null && !originalMessage.isEmpty()) {
        msg = msg + ": " + originalMessage;
      }
      if (msg.endsWith("Java heap space")) {
        msg += ". Configure Gradle memory settings using '-Xmx' JVM option (e.g. '-Xmx2048m'.)";
      } else if (!msg.endsWith(".")) {
        msg += ".";
      }
      msg += EMPTY_LINE + OPEN_GRADLE_SETTINGS;
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof ClassNotFoundException) {
      String msg = String.format("Unable to load class '%1$s'.", rootCause.getMessage()) + EMPTY_LINE +
                   UNEXPECTED_ERROR_FILE_BUG;
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof UnknownHostException) {
      String msg = String.format("Unknown host '%1$s'.", rootCause.getMessage()) +
                   EMPTY_LINE + "Please ensure the host name is correct. " +
                   SET_UP_HTTP_PROXY;
      // Location of build.gradle is useless for this error. Omitting it.
      return createUserFriendlyError(msg, null);
    }

    if (rootCause instanceof ConnectException) {
      String msg = rootCause.getMessage();
      if (msg != null && msg.contains("timed out")) {
        msg += msg.endsWith(".") ? " " : ". ";
        msg += SET_UP_HTTP_PROXY;
        return createUserFriendlyError(msg, null);
      }
    }

    if (rootCause instanceof RuntimeException) {
      String msg = rootCause.getMessage();

      if (msg != null && msg.startsWith(UNSUPPORTED_GRADLE_VERSION_ERROR)) {
        if (!msg.endsWith(".")) {
          msg += ".";
        }
        msg += EMPTY_LINE + OPEN_GRADLE_SETTINGS;
        // Location of build.gradle is useless for this error. Omitting it.
        return createUserFriendlyError(msg, null);
      }

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

    return createUserFriendlyError(rootCause.getMessage(), location);
  }

  @NotNull
  private static Pair<Throwable, String> getRootCauseAndLocation(@NotNull Throwable error) {
    Throwable rootCause = error;
    String location = null;
    while (true) {
      if (location == null) {
        location = getLocationFrom(rootCause);
      }
      if (rootCause.getCause() == null || rootCause.getCause().getMessage() == null) {
        break;
      }
      rootCause = rootCause.getCause();
    }
    //noinspection ConstantConditions
    return Pair.create(rootCause, location);
  }

  @Nullable
  private static String getLocationFrom(@NotNull Throwable error) {
    String errorToString = error.toString();
    if (errorToString != null && errorToString.startsWith(LocationAwareException.class.getName())) {
      // LocationAwareException is never passed, but converted into a PlaceholderException that has the toString value of the original
      // LocationAwareException.
      String location = error.getMessage();
      if (location != null && location.startsWith("Build file '")) {
        // Only the first line contains the location of the error. Discard the rest.
        Iterable<String> lines = Splitter.on('\n').split(location);
        return lines.iterator().next();
      }
    }
    return null;
  }

  private static boolean isOldGradleVersion(@NotNull Throwable error) {
    if (error instanceof ClassNotFoundException) {
      String msg = error.getMessage();
      if (msg != null && msg.contains(ToolingModelBuilderRegistry.class.getName())) {
        return true;
      }
    }
    String errorToString = error.toString();
    return errorToString != null && errorToString.startsWith("org.gradle.api.internal.MissingMethodException");
  }

  @NotNull
  private static ExternalSystemException createUserFriendlyError(@NotNull String msg, @Nullable String location) {
    String newMsg = msg;
    if (!newMsg.isEmpty() && Character.isLowerCase(newMsg.charAt(0))) {
      // Message starts with lower case letter. Sentences should start with uppercase.
      newMsg = "Cause: " + newMsg;
    }

    if (!Strings.isNullOrEmpty(location)) {
      newMsg = newMsg + EMPTY_LINE + location;
    }
    return new ExternalSystemException(newMsg);
  }
}
