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

import com.android.tools.analytics.UsageTracker;
import com.google.common.annotations.VisibleForTesting;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.LocationAwareExternalSystemException;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectImportErrorHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE_SYNC;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.UNKNOWN_GRADLE_FAILURE;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.openapi.util.text.StringUtil.splitByLines;

/**
 * Provides better error messages for Android projects import failures.
 */
public class ProjectImportErrorHandler extends AbstractProjectImportErrorHandler {

  private static final Pattern ERROR_LOCATION_PATTERN = Pattern.compile(".* file '(.*)'( line: ([\\d]+))?");

  @Override
  @Nullable
  public ExternalSystemException getUserFriendlyError(@NotNull Throwable error,
                                                      @NotNull String projectPath,
                                                      @Nullable String buildFilePath) {
    if (error instanceof ExternalSystemException) {
      // This is already a user-friendly error.
      logSyncFailure();
      return (ExternalSystemException)error;
    }

    Pair<Throwable, String> rootCauseAndLocation = getRootCauseAndLocation(error);
    Throwable rootCause = rootCauseAndLocation.getFirst();

    // Create ExternalSystemException or LocationAwareExternalSystemException, so that it goes to SyncErrorHandlers directly.
    String location = rootCauseAndLocation.getSecond();
    String errMessage;
    if (rootCause.getMessage() == null) {
      StringWriter writer = new StringWriter();
      rootCause.printStackTrace(new PrintWriter(writer));
      errMessage = writer.toString();
    }
    else {
      errMessage = rootCause.getMessage();
    }

    if (!errMessage.isEmpty() && Character.isLowerCase(errMessage.charAt(0))) {
      // Message starts with lower case letter. Sentences should start with uppercase.
      errMessage = "Cause: " + errMessage;
    }

    ExternalSystemException exception = null;
    if (isNotEmpty(location)) {
      Pair<String, Integer> pair = getErrorLocation(location);
      if (pair != null) {
        exception = new LocationAwareExternalSystemException(errMessage, pair.first, pair.getSecond());
      }
    }
    if (exception == null) {
      exception = new ExternalSystemException(errMessage);
    }
    exception.initCause(rootCause);
    return exception;
  }

  public void logSyncFailure() {
    AndroidStudioEvent.Builder event = AndroidStudioEvent.newBuilder();
    // @formatter:off
      event.setCategory(GRADLE_SYNC)
           .setKind(GRADLE_SYNC_FAILURE)
           .setGradleSyncFailure(UNKNOWN_GRADLE_FAILURE);
    // @formatter:on
    UsageTracker.getInstance().log(event);
  }

  // The default implementation in IDEA only retrieves the location in build.gradle files. This implementation also handle location in
  // settings.gradle file.
  @Override
  @Nullable
  public String getLocationFrom(@NotNull Throwable error) {
    String errorToString = error.toString();
    if (errorToString.contains("LocationAwareException")) {
      // LocationAwareException is never passed, but converted into a PlaceholderException that has the toString value of the original
      // LocationAwareException.
      String location = error.getMessage();
      if (location != null && (location.startsWith("Build file '") || location.startsWith("Settings file '"))) {
        // Only the first line contains the location of the error. Discard the rest.
        String[] lines = splitByLines(location);
        return lines.length > 0 ? lines[0] : null;
      }
    }
    return null;
  }

  @Override
  @NotNull
  public ExternalSystemException createUserFriendlyError(@NotNull String msg, @Nullable String location, @NotNull String... quickFixes) {
    if (isNotEmpty(location)) {
      Pair<String, Integer> pair = getErrorLocation(location);
      if (pair != null) {
        return new LocationAwareExternalSystemException(msg, pair.first, pair.getSecond(), quickFixes);
      }
    }
    return new ExternalSystemException(msg, null, quickFixes);
  }

  @VisibleForTesting
  @Nullable
  static Pair<String, Integer> getErrorLocation(@NotNull String location) {
    Matcher matcher = ERROR_LOCATION_PATTERN.matcher(location);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      int line = -1;
      String lineAsText = matcher.group(3);
      if (lineAsText != null) {
        try {
          line = Integer.parseInt(lineAsText);
        }
        catch (NumberFormatException e) {
          // ignored.
        }
      }
      return Pair.create(filePath, line);
    }
    return null;
  }
}
