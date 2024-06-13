// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import com.android.tools.analytics.AnalyticsSettings;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.modes.essentials.EssentialsModeToggleAction;
import com.android.tools.idea.startup.Actions;
import com.android.tools.idea.util.VirtualFileSystemOpener;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Anchor;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.impl.ActionConfigurationCustomizer;
import org.jetbrains.annotations.NotNull;

public final class AndroidPlugin {

  public AndroidPlugin() {
    VirtualFileSystemOpener.INSTANCE.mount();
  }

  static final class ActionCustomizer implements ActionConfigurationCustomizer {
    @Override
    public void customize(@NotNull ActionManager actionManager) {
      if (!IdeInfo.getInstance().isAndroidStudio()) {
        initializeForNonStudio();
      } else {
        overrideEssentialHighlightingAction(actionManager);
      }
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

  /**
   * Set up "Essential Highlighting" action to be behind feature flag.
   * <p>
   * In Intellij platform it is currently internal action, and only available in internal mode.
   * For Android Studio make it non-internal and controlled by server side flag.
   */
  private static void overrideEssentialHighlightingAction(ActionManager actionManager) {
    ToggleAction studioAction = new EssentialsModeToggleAction();
    // when using Essentials mode, don't show essential-highlighting notifications
    PropertiesComponent.getInstance().setValue("ignore.essential-highlighting.mode", true);
    if (actionManager.getAction("ToggleEssentialHighlighting") != null) {
      Actions.replaceAction(actionManager, "ToggleEssentialHighlighting", studioAction);
    } else {
      AnAction group = actionManager.getAction("PowerSaveGroup");
      ((DefaultActionGroup)group).add(studioAction, Constraints.LAST, actionManager);
    }
  }

}
