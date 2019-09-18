// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.actions.AndroidStudioGradleAction;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.util.VirtualFileSystemOpener;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.startup.Actions.moveAction;

final class AndroidPlugin {
  private static final String GROUP_ANDROID_TOOLS = "AndroidToolsGroup";

  AndroidPlugin() {
    VirtualFileSystemOpener.INSTANCE.mount();
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      initializeForNonStudio();
    }
  }

  static final class AndroidActionConfigurationCustomizer implements ActionConfigurationCustomizer {
    @Override
    public void customize(@NotNull ActionManager manager) {
      if (!IdeInfo.getInstance().isAndroidStudio()) {
        // Move the "Sync Project with Gradle Files" from the File menu to Tools > Android.
        moveAction("Android.SyncProject", IdeActions.GROUP_FILE, GROUP_ANDROID_TOOLS, new Constraints(Anchor.FIRST, null), manager);
        // Move the "Sync Project with Gradle Files" toolbar button to a less prominent place.
        moveAction("Android.MainToolBarGradleGroup", IdeActions.GROUP_MAIN_TOOLBAR, "Android.MainToolBarActionGroup",
                   new Constraints(Anchor.LAST, null), manager);
      }

      setUpActionsUnderFlag(manager);
    }
  }

  /**
   * Initializes the Android plug-in when it runs outside of Android Studio.
   * Reduces prominence of the Android related UI elements to keep low profile.
   */
  private static void initializeForNonStudio() {
    AnalyticsSettings.initialize(new LogWrapper(Logger.getInstance(AndroidPlugin.class)), null);
    AnalyticsSettings.setOptedIn(false);
    UsageTracker.setIdeBrand(AndroidStudioEvent.IdeBrand.INTELLIJ);
    UsageTracker.initialize(JobScheduler.getScheduler());
  }

  private static void setUpActionsUnderFlag(@NotNull ActionManager actionManager) {
    // TODO: Once the StudioFlag is removed, the configuration type registration should move to the
    // android-plugin.xml file.
    if (!StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.get()) {
      return;
    }

    AnAction parentGroup = actionManager.getAction("BuildMenu");
    if (!(parentGroup instanceof DefaultActionGroup)) {
      return;
    }

    // Create new "Build Bundle(s) / APK(s)" group
    final String groupId = "Android.BuildApkOrBundle";
    DefaultActionGroup group = new DefaultActionGroup("Build Bundle(s) / APK(s)", true) {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(AndroidStudioGradleAction.isAndroidGradleProject(e));
      }
    };
    actionManager.registerAction(groupId, group);
    ((DefaultActionGroup)parentGroup).add(group, new Constraints(Anchor.BEFORE, "Android.GenerateSignedApk"), actionManager);

    // Move "Build" actions to new "Build Bundle(s) / APK(s)" group
    moveAction("Android.BuildApk", "BuildMenu", groupId, new Constraints(Anchor.FIRST, null), actionManager);
    moveAction("Android.BuildBundle", "BuildMenu", groupId, new Constraints(Anchor.AFTER, null), actionManager);
  }
}
