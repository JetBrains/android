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
package com.android.tools.idea.gradle.service.notification.hyperlink;

import com.android.SdkConstants;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.SdkConstants.GRADLE_PLUGIN_RECOMMENDED_VERSION;
import static com.android.tools.idea.gradle.util.GradleUtil.updateGradlePluginVersionAndNotifyFailure;
import static com.intellij.ide.BrowserUtil.browse;

public class FixAndroidGradlePluginVersionHyperlink extends NotificationHyperlink {
  @NotNull private final String myModelVersion;
  @Nullable private final String myGradleVersion;

  private final boolean myOpenMigrationGuide;

  /**
   * Creates a new {@link FixAndroidGradlePluginVersionHyperlink}. This constructor updates the Gradle model to the version in
   * {@link SdkConstants#GRADLE_PLUGIN_RECOMMENDED_VERSION} and Gradle to the version in {@link SdkConstants#GRADLE_LATEST_VERSION}.
   *
   * @param openMigrationGuide indicates whether the migration guide to the Android Gradle model version 1.0 (from an older version) should
   *                           be opened in the browser.
   */
  public FixAndroidGradlePluginVersionHyperlink(boolean openMigrationGuide) {
    this(GRADLE_PLUGIN_RECOMMENDED_VERSION, GRADLE_LATEST_VERSION, openMigrationGuide);
  }

  /**
   * Creates a new {@link FixAndroidGradlePluginVersionHyperlink}.
   *
   * @param modelVersion       the version to update the Android Gradle model to.
   * @param gradleVersion      the version of Gradle to update to. This can be {@code null} if only the model version needs to be updated.
   * @param openMigrationGuide indicates whether the migration guide to the Android Gradle model version 1.0 (from an older version) should
   *                           be opened in the browser.
   */
  public FixAndroidGradlePluginVersionHyperlink(@NotNull String modelVersion,
                                                @Nullable String gradleVersion,
                                                boolean openMigrationGuide) {
    super("fixGradleElements",
          openMigrationGuide ? "Open migration guide, fix plugin version and sync project" : "Fix plugin version and sync project");
    myModelVersion = modelVersion;
    myGradleVersion = gradleVersion;
    myOpenMigrationGuide = false;
  }

  @Override
  public void execute(@NotNull Project project) {
    if (myOpenMigrationGuide) {
      browse("http://tools.android.com/tech-docs/new-build-system/migrating-to-1-0-0");
    }
    updateGradlePluginVersionAndNotifyFailure(project, myModelVersion, myGradleVersion, false);
  }
}
