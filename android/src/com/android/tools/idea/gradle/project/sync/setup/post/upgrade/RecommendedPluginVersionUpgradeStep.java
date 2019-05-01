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

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;

import com.android.annotations.concurrency.Slow;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.plugin.AndroidPluginInfo;
import com.android.tools.idea.gradle.plugin.LatestKnownPluginVersionProvider;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater.UpdateResult;
import com.android.tools.idea.gradle.project.sync.setup.post.PluginVersionUpgradeStep;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RecommendedPluginVersionUpgradeStep implements PluginVersionUpgradeStep {

  public static final ExtensionPointName<RecommendedPluginVersionUpgradeStep>
    EXTENSION_POINT_NAME = ExtensionPointName.create("com.android.gradle.sync.recommendedPluginVersionUpgradeStep");

  @NotNull
  public static RecommendedPluginVersionUpgradeStep[] getExtensions() {
    return EXTENSION_POINT_NAME.getExtensions();
  }

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
  @Slow
  public boolean checkUpgradable(@NotNull Project project, @NotNull AndroidPluginInfo pluginInfo) {
    if (myUpgradeReminder.shouldRecommendUpgrade(project) && shouldRecommendUpgrade(pluginInfo)) {
      GradleVersion current = pluginInfo.getPluginVersion();
      assert current != null;
      GradleVersion recommended = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get());

      AndroidPluginVersionUpdater updater = AndroidPluginVersionUpdater.getInstance(project);
      if (updater.canDetectPluginVersionToUpdate(recommended)) {
        return true;
      }
    }
    return false;
  }

  @Override
  @Slow
  public boolean performUpgradeAndSync(@NotNull Project project, @NotNull AndroidPluginInfo pluginInfo) {
    if (!checkUpgradable(project, pluginInfo)) {
      return false;
    }

    GradleVersion current = pluginInfo.getPluginVersion();
    assert current != null;
    GradleVersion recommended = GradleVersion.parse(pluginInfo.getLatestKnownPluginVersionProvider().get());

    AndroidPluginVersionUpdater updater = AndroidPluginVersionUpdater.getInstance(project);
    if (updater.canDetectPluginVersionToUpdate(recommended)) {
      Computable<Boolean> promptUserTask = () -> {
        RecommendedPluginVersionUpgradeDialog updateDialog = myUpgradeDialogFactory.create(project, current, recommended);
        return updateDialog.showAndGet();
      };
      boolean userAcceptsUpgrade;
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        userAcceptsUpgrade = promptUserTask.compute();
      }
      else {
        final Ref<Boolean> result = Ref.create();
        ApplicationManager.getApplication().invokeAndWait(() -> result.set(promptUserTask.compute()), ModalityState.NON_MODAL);
        userAcceptsUpgrade = result.get();
      }

      if (userAcceptsUpgrade) {
        GradleVersion latestGradleVersion = GradleVersion.parse(GRADLE_LATEST_VERSION);
        UpdateResult result = updater.updatePluginVersionAndSync(recommended, latestGradleVersion);
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
    GradleVersion recommended = GradleVersion.parse(LatestKnownPluginVersionProvider.INSTANCE.get());
    return shouldRecommendUpgrade(recommended, current);
  }

  @VisibleForTesting
  static boolean shouldRecommendUpgrade(@NotNull GradleVersion recommended, @Nullable GradleVersion current) {
    if (current != null) {
      if (recommended.isSnapshot() && current.compareIgnoringQualifiers(recommended) == 0) {
        // Do not upgrade to snapshot version when major versions are same.
        return false;
      }
      if (current.isPreview() && recommended.isPreview() && !recommended.isSnapshot()) {
        // Upgrade from preview to non-snapshot preview version is handled by force upgrade.
        return false;
      }
      if (!current.isPreview() && recommended.isPreview() && current.compareIgnoringQualifiers(recommended) < 0) {
        // Stable to new preview version. e.g 3.3.0 to 3.4.0-alpha01
        return true;
      }
      return current.compareTo(recommended) < 0;
    }
    return false;
  }
}
