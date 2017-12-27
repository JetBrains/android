/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

class TimeBasedUpgradeReminder {
  private static final String SYNC_PLUGIN_LAST_UPGRADE_TIMESTAMP_PROPERTY = "sync.plugin.last.upgrade.timestamp";

  boolean shouldRecommendUpgrade(@NotNull Project project) {
    return shouldRecommendUpgrade(project, System.currentTimeMillis());
  }

  @VisibleForTesting
  boolean shouldRecommendUpgrade(@NotNull Project project, long currentTimeInMs) {
    String lastTimestampValue = getStoredTimestamp(project);
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

  @VisibleForTesting
  @Nullable
  String getStoredTimestamp(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getValue(SYNC_PLUGIN_LAST_UPGRADE_TIMESTAMP_PROPERTY);
  }

  void storeLastUpgradeRecommendation(@NotNull Project project) {
    storeLastUpgradeRecommendation(project, System.currentTimeMillis());
  }

  @VisibleForTesting
  void storeLastUpgradeRecommendation(@NotNull Project project, long currentTimeInMs) {
    PropertiesComponent.getInstance(project).setValue(SYNC_PLUGIN_LAST_UPGRADE_TIMESTAMP_PROPERTY, String.valueOf(currentTimeInMs));
  }
}
