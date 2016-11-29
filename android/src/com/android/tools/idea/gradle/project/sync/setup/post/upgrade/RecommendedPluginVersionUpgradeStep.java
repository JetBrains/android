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
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginGeneration;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.project.sync.setup.post.PluginVersionUpgradeStep;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;

public class RecommendedPluginVersionUpgradeStep extends PluginVersionUpgradeStep {
  @Override
  public boolean checkAndPerformUpgrade(@NotNull Project project, @NotNull AndroidPluginInfo pluginInfo) {
    if (shouldRecommendUpgrade(pluginInfo)) {
      GradleVersion current = pluginInfo.getPluginVersion();
      assert current != null;
      AndroidPluginGeneration pluginGeneration = pluginInfo.getPluginGeneration();
      GradleVersion recommended = GradleVersion.parse(pluginGeneration.getLatestKnownVersion());
      RecommendedPluginVersionUpdateDialog updateDialog = new RecommendedPluginVersionUpdateDialog(project, current, recommended);
      boolean userAcceptsUpgrade = updateDialog.showAndGet();

      if (userAcceptsUpgrade) {
        AndroidPluginVersionUpdater updater = AndroidPluginVersionUpdater.getInstance(project);
        GradleVersion latestGradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION);
        AndroidPluginVersionUpdater.UpdateResult result = updater.updatePluginVersionAndSync(recommended, latestGradleVersion, false);
        if (result.versionUpdateSuccess()) {
          // plugin version updated and a project sync was requested. No need to continue.
          return true;
        }
      }
    }
    return false;
  }

  private static boolean shouldRecommendUpgrade(@NotNull AndroidPluginInfo androidPluginInfo) {
    return shouldRecommendUpgradeBasedOnPluginVersion(androidPluginInfo);
  }

  private static boolean shouldRecommendUpgradeBasedOnPluginVersion(@NotNull AndroidPluginInfo androidPluginInfo) {
    GradleVersion current = androidPluginInfo.getPluginVersion();
    String recommended = androidPluginInfo.getPluginGeneration().getLatestKnownVersion();
    return current != null && current.compareTo(recommended) < 0;
  }
}
