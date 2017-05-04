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
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater.UpdateResult;
import com.android.tools.idea.gradle.project.sync.setup.post.PluginVersionUpgradeStep;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.intellij.util.ui.UIUtil.invokeAndWaitIfNeeded;

public class RecommendedPluginVersionUpgradeStep extends PluginVersionUpgradeStep {
  @NotNull private final RecommendedPluginVersionUpgradeDialog.Factory myUpgradeDialogFactory;
  @NotNull private final TimeBasedUpgradeReminder myUpgradeReminder;

  @SuppressWarnings("unused") // Invoked by IDEA.
  public RecommendedPluginVersionUpgradeStep() {
    this(new RecommendedPluginVersionUpgradeDialog.Factory(), new TimeBasedUpgradeReminder());
  }

  @VisibleForTesting
  RecommendedPluginVersionUpgradeStep(@NotNull RecommendedPluginVersionUpgradeDialog.Factory upgradeDialogFactory,
                                      @NotNull TimeBasedUpgradeReminder upgradeReminder) {
    myUpgradeDialogFactory = upgradeDialogFactory;
    myUpgradeReminder = upgradeReminder;
  }

  @Override
  public boolean checkAndPerformUpgrade(@NotNull Project project, @NotNull AndroidPluginInfo pluginInfo) {
    if (myUpgradeReminder.shouldRecommendUpgrade(project) && shouldRecommendUpgrade(pluginInfo)) {
      GradleVersion current = pluginInfo.getPluginVersion();
      assert current != null;
      AndroidPluginGeneration pluginGeneration = pluginInfo.getPluginGeneration();
      GradleVersion recommended = GradleVersion.parse(pluginGeneration.getLatestKnownVersion());

      Computable<Boolean> promptUserTask = () -> {
        RecommendedPluginVersionUpgradeDialog updateDialog = myUpgradeDialogFactory.create(project, current, recommended);
        return updateDialog.showAndGet();
      };
      boolean userAcceptsUpgrade;
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        userAcceptsUpgrade = promptUserTask.compute();
      }
      else {
        userAcceptsUpgrade = invokeAndWaitIfNeeded(promptUserTask);
      }

      if (userAcceptsUpgrade) {
        AndroidPluginVersionUpdater updater = AndroidPluginVersionUpdater.getInstance(project);
        GradleVersion latestGradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION);
        UpdateResult result = updater.updatePluginVersionAndSync(recommended, latestGradleVersion,
                                                                 false /* do not invalidate last sync if update fails */);
        if (result.versionUpdateSuccess()) {
          // plugin version updated and a project sync was requested. No need to continue.
          return true;
        }
      }
    }
    return false;
  }

  private static boolean shouldRecommendUpgrade(@NotNull AndroidPluginInfo androidPluginInfo) {
    GradleVersion current = androidPluginInfo.getPluginVersion();
    GradleVersion recommended = GradleVersion.parse(androidPluginInfo.getPluginGeneration().getLatestKnownVersion());
    return shouldRecommendUpgrade(recommended, current);
  }

  @VisibleForTesting
  static boolean shouldRecommendUpgrade(@NotNull GradleVersion recommended, @Nullable GradleVersion current) {
    if (current != null) {
      if (recommended.isSnapshot() && current.getPreviewType() != null && current.compareIgnoringQualifiers(recommended) == 0) {
        // e.g recommended: 2.3.0-dev and current: 2.3.0-alpha1
        return false;
      }
      return current.compareTo(recommended) < 0;
    }
    return false;
  }
}
