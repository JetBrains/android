/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade;

import static com.android.tools.idea.gradle.project.sync.hyperlink.SearchInBuildFilesHyperlink.searchInBuildFiles;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.serviceContainer.NonInjectable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface AndroidPluginVersionUpdater {
  static AndroidPluginVersionUpdater getInstance(@NotNull Project project) {
    return project.getService(AndroidPluginVersionUpdater.class);
  }

  /**
   * @param pluginVersion the Android Gradle plugin version to update to
   * @param gradleVersion the Gradle version to update to
   * @return whether or not the update of the Android Gradle plugin OR Gradle version was successful.
   */
  boolean updatePluginVersion(@NotNull GradleVersion pluginVersion, @Nullable GradleVersion gradleVersion);

  /**
   * Updates the plugin version and, optionally, the Gradle version used by the project.
   *
   * @param pluginVersion    the plugin version to update to.
   * @param gradleVersion    the version of Gradle to update to (optional.)
   * @param oldPluginVersion the version of plugin from which we update. Used because of b/130738995.
   * @return the result of the update operation.
   */
  UpdateResult updatePluginVersion(
    @NotNull GradleVersion pluginVersion,
    @Nullable GradleVersion gradleVersion,
    @Nullable GradleVersion oldPluginVersion
  );

  class UpdateResult {
    @Nullable private Throwable myPluginVersionUpdateError;
    @Nullable private Throwable myGradleVersionUpdateError;

    private boolean myPluginVersionUpdated;
    private boolean myGradleVersionUpdated;

    protected boolean complete;

    public UpdateResult() {
    }

    @Nullable
    public Throwable getPluginVersionUpdateError() {
      return myPluginVersionUpdateError;
    }

    void setPluginVersionUpdateError(@NotNull Throwable error) {
      myPluginVersionUpdateError = error;
    }

    @Nullable
    public Throwable getGradleVersionUpdateError() {
      return myGradleVersionUpdateError;
    }

    void setGradleVersionUpdateError(@NotNull Throwable error) {
      myGradleVersionUpdateError = error;
    }

    public boolean isPluginVersionUpdated() {
      return myPluginVersionUpdated;
    }

    void pluginVersionUpdated() {
      myPluginVersionUpdated = true;
    }

    public boolean isGradleVersionUpdated() {
      return myGradleVersionUpdated;
    }

    void gradleVersionUpdated() {
      myGradleVersionUpdated = true;
    }

    public boolean versionUpdateSuccess() {
      return (myPluginVersionUpdated || myGradleVersionUpdated) && myPluginVersionUpdateError == null && myGradleVersionUpdateError == null;
    }
  }
}
