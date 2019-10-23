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
package com.android.tools.idea.gradle.project.sync.setup.post;

import com.android.annotations.concurrency.Slow;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider;
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.ForcedPluginVersionUpgradeStep;
import com.android.tools.idea.gradle.project.sync.setup.post.upgrade.RecommendedPluginVersionUpgradeStep;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;

public class PluginVersionUpgrade {
  @NotNull private final Project myProject;
  @NotNull private final ForcedPluginVersionUpgradeStep[] myForcedUpgradeSteps;
  @NotNull private final RecommendedPluginVersionUpgradeStep[] myRecommendedUpgradeSteps;

  @NotNull
  public static PluginVersionUpgrade getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PluginVersionUpgrade.class);
  }

  public PluginVersionUpgrade(@NotNull Project project) {
    this(project, ForcedPluginVersionUpgradeStep.getExtensions(), RecommendedPluginVersionUpgradeStep.getExtensions());
  }

  @VisibleForTesting
  public PluginVersionUpgrade(@NotNull Project project,
                              @NotNull ForcedPluginVersionUpgradeStep[] forcedUpgradeSteps,
                              @NotNull RecommendedPluginVersionUpgradeStep[] recommendedUpgradeSteps) {
    myProject = project;
    myForcedUpgradeSteps = forcedUpgradeSteps;
    myRecommendedUpgradeSteps = recommendedUpgradeSteps;
  }

  @Slow
  public boolean isForcedUpgradable() {
    AndroidPluginInfo pluginInfo = AndroidPluginInfo.find(myProject);
    if (pluginInfo == null) {
      return false;
    }
    return Arrays.stream(myForcedUpgradeSteps).anyMatch(it -> it.checkUpgradable(myProject, pluginInfo));
  }

  public boolean isRecommendedUpgradable() {
    AndroidPluginInfo pluginInfo = AndroidPluginInfo.find(myProject);
    if (pluginInfo == null) {
      return false;
    }
    return Arrays.stream(myRecommendedUpgradeSteps).anyMatch(it -> it.checkUpgradable(myProject, pluginInfo));
  }

  public boolean performForcedUpgrade() {
    return performUpgrade(myForcedUpgradeSteps);
  }

  public boolean performRecommendedUpgrade() {
    return performUpgrade(myRecommendedUpgradeSteps);
  }

  private boolean performUpgrade(@NotNull PluginVersionUpgradeStep[] steps) {
    AndroidPluginInfo pluginInfo = AndroidPluginInfo.find(myProject);
    if (pluginInfo == null) {
      getLog().warn("Unable to obtain application's Android Project");
      return false;
    }
    log(pluginInfo);
    return Arrays.stream(steps).anyMatch(it -> {
      if (it.checkUpgradable(myProject, pluginInfo)) {
        return it.performUpgradeAndSync(myProject, pluginInfo);
      }
      return false;
    });
  }

  /**
   * Checks if the Android plugin used in the project needs to be upgraded, and if so, performs the upgrade.
   * TODO(b/127454467): remove this function after StudioFlags.BALLOON_UPGRADE_NOTIFICATION is removed.
   *
   * @return {@code true} if an upgrade was needed and was successfully performed; {@code false} otherwise.
   */
  @Slow
  public boolean checkAndPerformUpgrade() {
    // We try force upgrade first then try recommended upgrade.
    if (performForcedUpgrade()) {
      return true;
    }
    return performRecommendedUpgrade();
  }

  private static void log(@NotNull AndroidPluginInfo pluginInfo) {
    GradleVersion current = pluginInfo.getPluginVersion();
    String recommended = LatestKnownPluginVersionProvider.INSTANCE.get();
    String message = String.format("Gradle model version: %1$s, recommended version for IDE: %2$s", current, recommended);
    getLog().info(message);
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(PluginVersionUpgrade.class);
  }
}
