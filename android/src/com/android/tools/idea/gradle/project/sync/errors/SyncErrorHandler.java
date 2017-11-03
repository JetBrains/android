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

import com.android.tools.analytics.UsageTracker;
import com.google.common.base.Splitter;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventCategory.GRADLE_SYNC;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.GRADLE_SYNC_FAILURE;
import static com.google.wireless.android.sdk.stats.AndroidStudioEvent.GradleSyncFailure.UNKNOWN_GRADLE_FAILURE;

public abstract class SyncErrorHandler {
  private static final ExtensionPointName<SyncErrorHandler> EXTENSION_POINT_NAME =
    ExtensionPointName.create("com.android.gradle.sync.syncErrorHandler");

  protected static final String EMPTY_LINE = "\n\n";
  protected static final NotificationType DEFAULT_NOTIFICATION_TYPE = NotificationType.ERROR;

  private static final Pattern ERROR_LOCATION_IN_FILE_PATTERN = Pattern.compile("Build file '(.*)' line: ([\\d]+)");
  private static final Pattern ERROR_IN_FILE_PATTERN = Pattern.compile("Build file '(.*)'");

  @NotNull
  public static SyncErrorHandler[] getExtensions() {
    return EXTENSION_POINT_NAME.getExtensions();
  }

  public abstract boolean handleError(@NotNull ExternalSystemException error,
                                      @NotNull NotificationData notification,
                                      @NotNull Project project);

  @NotNull
  protected Throwable getRootCause(@NotNull Throwable error) {
    Throwable rootCause = error;
    while (true) {
      if (rootCause.getCause() == null || rootCause.getCause().getMessage() == null) {
        break;
      }
      rootCause = rootCause.getCause();
    }
    return rootCause;
  }

  protected static void updateUsageTracker() {
    updateUsageTracker(null, null);
  }

  protected static void updateUsageTracker(@NotNull GradleSyncFailure syncFailure) {
    updateUsageTracker(syncFailure, null);
  }

  protected static void updateUsageTracker(@Nullable GradleSyncFailure gradleSyncFailure, @Nullable String gradleMissingSignature) {
    AndroidStudioEvent.Builder builder =
      AndroidStudioEvent.newBuilder();
    if (gradleSyncFailure == null) {
      gradleSyncFailure = UNKNOWN_GRADLE_FAILURE;
    }
    // @formatter:off
    builder.setCategory(GRADLE_SYNC)
           .setKind(GRADLE_SYNC_FAILURE)
           .setGradleSyncFailure(gradleSyncFailure);
    // @formatter:on
    if (gradleMissingSignature != null) {
      builder.setGradleMissingSignature(gradleMissingSignature);
    }
    UsageTracker.getInstance().log(builder);
  }

  @NotNull
  protected static String getFirstLineMessage(@NotNull String text) {
    List<String> lines = getMessageLines(text);
    if (lines.isEmpty()) {
      return "";
    }
    return lines.get(0);
  }

  @NotNull
  protected static List<String> getMessageLines(@NotNull String text) {
    return Splitter.on('\n').omitEmptyStrings().trimResults().splitToList(text);
  }

  @Nullable
  public static Pair<String, Integer> getErrorLocation(@NotNull String msg) {
    Matcher matcher = ERROR_LOCATION_IN_FILE_PATTERN.matcher(msg);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      int line = -1;
      try {
        line = Integer.parseInt(matcher.group(2));
      }
      catch (NumberFormatException e) {
        // ignored.
      }
      return Pair.create(filePath, line);
    }

    matcher = ERROR_IN_FILE_PATTERN.matcher(msg);
    if (matcher.matches()) {
      String filePath = matcher.group(1);
      return Pair.create(filePath, -1);
    }
    return null;
  }
}