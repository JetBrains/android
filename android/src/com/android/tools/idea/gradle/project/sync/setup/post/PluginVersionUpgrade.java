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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class PluginVersionUpgrade {
  @NotNull private final Project myProject;
  @NotNull private final PluginVersionUpgradeStep[] myUpgradeSteps;

  @NotNull
  public static PluginVersionUpgrade getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, PluginVersionUpgrade.class);
  }

  public PluginVersionUpgrade(@NotNull Project project) {
    this(project, PluginVersionUpgradeStep.getExtensions());
  }

  @VisibleForTesting
  PluginVersionUpgrade(@NotNull Project project, @NotNull PluginVersionUpgradeStep... upgradeSteps) {
    myProject = project;
    myUpgradeSteps = upgradeSteps;
  }

  /**
   * Checks if the Android plugin used in the project needs to be upgraded, and if so, performs the upgrade.
   *
   * @return {@code true} if an upgrade was needed and was successfully performed; {@code false} otherwise.
   */
  public boolean checkAndPerformUpgrade() {
    AndroidPluginInfo pluginInfo = AndroidPluginInfo.find(myProject);
    if (pluginInfo == null) {
      getLog().warn("Unable to obtain application's Android Project");
      return false;
    }

    log(pluginInfo);
    for (PluginVersionUpgradeStep upgradeStep : myUpgradeSteps) {
      if (upgradeStep.checkAndPerformUpgrade(myProject, pluginInfo)) {
        // plugin was updated and sync requested. No need to continue.
        return true;
      }
    }

    return false;
  }

  private static void log(@NotNull AndroidPluginInfo pluginInfo) {
    GradleVersion current = pluginInfo.getPluginVersion();
    String recommended = pluginInfo.getPluginGeneration().getLatestKnownVersion();
    String message = String.format("Gradle model version: %1$s, recommended version for IDE: %2$s", current, recommended);
    getLog().info(message);
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(PluginVersionUpgrade.class);
  }
}
