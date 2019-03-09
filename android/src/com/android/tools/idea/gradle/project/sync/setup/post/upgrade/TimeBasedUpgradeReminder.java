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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class TimeBasedUpgradeReminder {
  public static final String SHOW_DO_NOT_ASK_TO_UPGRADE_PLUGIN_PROPERTY_NAME = "show.do.not.ask.upgrade.gradle.plugin";
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

  /**
   * Check if user clicked "Don't ask me again" before.
   */
  boolean shouldAskForUpgrade(@NotNull Project project) {
    // don't ask for upgrading if we even cannot find gradle plugin info
    AndroidPluginInfo info = AndroidPluginInfo.find(project);
    if (info != null) {
      GradleVersion currentVersion = info.getPluginVersion();
      if (currentVersion != null) {
        return !Objects.equals(currentVersion.toString(), getDoNotAskAgainVersion(project));
      }
    }
    return false;
  }

  void setDoNotAskAgainVersion(@NotNull Project project, @NotNull String ignoredVersion) {
    PropertiesComponent.getInstance(project).setValue(SHOW_DO_NOT_ASK_TO_UPGRADE_PLUGIN_PROPERTY_NAME, ignoredVersion);
  }

  /**
   * If there is no do not ask again version, the empty string will be return.
   */
  @NotNull
  String getDoNotAskAgainVersion(@NotNull Project project) {
    return PropertiesComponent.getInstance(project).getValue(SHOW_DO_NOT_ASK_TO_UPGRADE_PLUGIN_PROPERTY_NAME, "");
  }
}
