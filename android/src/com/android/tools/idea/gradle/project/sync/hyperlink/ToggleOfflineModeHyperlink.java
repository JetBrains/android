/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED;

public class ToggleOfflineModeHyperlink extends NotificationHyperlink {
  private final boolean myEnableOfflineMode;

  @Nullable
  public static ToggleOfflineModeHyperlink enableOfflineMode(@NotNull Project project) {
    GradleSettings settings = GradleSettings.getInstance(project);
    return settings.isOfflineWork() ? null : new ToggleOfflineModeHyperlink(true);
  }

  @Nullable
  public static ToggleOfflineModeHyperlink disableOfflineMode(@NotNull Project project) {
    GradleSettings settings = GradleSettings.getInstance(project);
    return !settings.isOfflineWork() ? null : new ToggleOfflineModeHyperlink(false);
  }

  private ToggleOfflineModeHyperlink(boolean enableOfflineMode) {
    super("toggle.offline.mode", getText(enableOfflineMode));
    myEnableOfflineMode = enableOfflineMode;
  }

  @NotNull
  private static String getText(boolean enableOfflineMode) {
    String msg = enableOfflineMode ? "Enable" : "Disable";
    return msg + " Gradle 'offline mode' and sync project";
  }

  @Override
  protected void execute(@NotNull Project project) {
    GradleSettings.getInstance(project).setOfflineWork(myEnableOfflineMode);
    GradleSyncInvoker.getInstance().requestProjectSyncAndSourceGeneration(project, TRIGGER_PROJECT_MODIFIED);
  }

  @TestOnly
  public boolean isEnableOfflineMode() {
    return myEnableOfflineMode;
  }
}
