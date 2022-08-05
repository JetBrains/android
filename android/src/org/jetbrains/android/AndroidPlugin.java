// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android;

import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.startup.Actions;
import com.android.tools.idea.util.VirtualFileSystemOpener;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class AndroidPlugin {

  public AndroidPlugin() {
    VirtualFileSystemOpener.INSTANCE.mount();
  }

  static final class AndroidPluginAppInitializer implements ApplicationInitializedListener {
    @Override
    public void componentsInitialized() {
      if (!IdeInfo.getInstance().isAndroidStudio()) {
        initializeForNonStudio();
      }
    }
  }

  static final class ActionCustomizer implements ActionConfigurationCustomizer {
    @Override
    public void customize(@NotNull ActionManager actionManager) {
      setUpActionsUnderFlag(actionManager);
    }
  }

  /**
   * Initializes the Android plug-in when it runs outside of Android Studio.
   * Reduces prominence of the Android related UI elements to keep low profile.
   */
  private static void initializeForNonStudio() {
    AnalyticsSettings.disable();
    UsageTracker.disable();
    UsageTracker.setIdeBrand(AndroidStudioEvent.IdeBrand.INTELLIJ);
  }

  private static void setUpActionsUnderFlag(@NotNull ActionManager actionManager) {
    // TODO: Once the StudioFlag is removed, the configuration type registration should move to the
    // android-plugin.xml file.
    if (StudioFlags.RUNDEBUG_ANDROID_BUILD_BUNDLE_ENABLED.get()) {
      AnAction parentGroup = actionManager.getAction("BuildMenu");
      if (parentGroup instanceof DefaultActionGroup) {
        // Create new "Build Bundle(s) / APK(s)" group
        final String groupId = "Android.BuildApkOrBundle";
        DefaultActionGroup group = new DefaultActionGroup("Build Bundle(s) / APK(s)", true) {
          @Override
          public void update(@NotNull AnActionEvent e) {
            Project project = e.getProject();
            e.getPresentation().setEnabledAndVisible(project != null && ProjectSystemUtil.requiresAndroidModel(project));
          }
        };
        actionManager.registerAction(groupId, group);
        ((DefaultActionGroup)parentGroup).add(group, new Constraints(Anchor.BEFORE, "Android.GenerateSignedApk"), actionManager);

        // Move "Build" actions to new "Build Bundle(s) / APK(s)" group
        Actions.moveAction(actionManager, "Android.BuildApk", "BuildMenu", groupId, new Constraints(Anchor.FIRST, null));
        Actions.moveAction(actionManager, "Android.BuildBundle", "BuildMenu", groupId, new Constraints(Anchor.AFTER, null));
      }
    }
  }
}
