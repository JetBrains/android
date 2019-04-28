/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TimeBasedMemorySettingsCheckerReminder {
  private static final String MEMORY_SETTINGS_DO_NOT_ASK_FOR_ALL_PROPERTY =
    "memory.settings.do.not.ask.ever";
  private static final String MEMORY_SETTINGS_DO_NOT_ASK_FOR_PROJECT_PROPERTY =
    "memory.settings.do.not.ask.for.project";
  private static final String MEMORY_SETTINGS_POST_SYNC_CHECK_TIMESTAMP_PROPERTY =
    "memory.settings.last.check.timestamp";

  boolean shouldCheck(@NotNull Project project) {
    return shouldCheck(project, System.currentTimeMillis());
  }

  // Returns true if the do-not-ask property is not set and last check is at least one day ago.
  boolean shouldCheck(@NotNull Project project, long currentTimeInMs) {
    if (shouldNotAsk(project)) {
      return false;
    }
    String lastTimestampValue = getStoredTimestamp();
    if (isNotEmpty(lastTimestampValue)) {
      try {
        long lastTimestamp = Long.parseLong(lastTimestampValue);
        long elapsed = currentTimeInMs - lastTimestamp;
        long days = MILLISECONDS.toDays(elapsed);
        return days >= 1;
      }
      catch (NumberFormatException ignored) {
      }
    }
    return true;
  }

  @Nullable
  String getStoredTimestamp() {
    return PropertiesComponent.getInstance().getValue(
      MEMORY_SETTINGS_POST_SYNC_CHECK_TIMESTAMP_PROPERTY);
  }

  void storeLastCheckTimestamp() {
    storeLastCheckTimestamp(System.currentTimeMillis());
  }

  void storeLastCheckTimestamp(long currentTimeInMs) {
    PropertiesComponent.getInstance().setValue(
      MEMORY_SETTINGS_POST_SYNC_CHECK_TIMESTAMP_PROPERTY, String.valueOf(currentTimeInMs));
  }

  void setDoNotAsk(@NotNull Project project) {
    PropertiesComponent.getInstance(project).setValue(
      MEMORY_SETTINGS_DO_NOT_ASK_FOR_PROJECT_PROPERTY, "true");
  }

  void setDoNotAskForApplication() {
    PropertiesComponent.getInstance().setValue(
      MEMORY_SETTINGS_DO_NOT_ASK_FOR_ALL_PROPERTY, "true");
  }

  private boolean shouldNotAsk(@NotNull Project project) {
    return PropertiesComponent.getInstance().getValue(
      MEMORY_SETTINGS_DO_NOT_ASK_FOR_ALL_PROPERTY, "false").equals("true") ||
      PropertiesComponent.getInstance(project).getValue(
        MEMORY_SETTINGS_DO_NOT_ASK_FOR_PROJECT_PROPERTY, "false").equals("true");
  }
}
