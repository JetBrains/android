/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android;

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.IdeInfo;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import static com.android.tools.idea.startup.Actions.moveAction;

public class AndroidPlugin implements ApplicationComponent {
  private static final String GROUP_ANDROID_TOOLS = "AndroidToolsGroup";
  private static final String GROUP_TOOLS = "ToolsMenu";

  @Override
  @NotNull
  public String getComponentName() {
    return "AndroidApplicationComponent";
  }

  @Override
  public void initComponent() {
    if (!IdeInfo.getInstance().isAndroidStudio()) {
      initializeForNonStudio();
    }
  }

  /**
   * Initializes the Android plug-in when it runs outside of Android Studio.
   * Reduces prominence of the Android related UI elements to keep low profile.
   */
  private static void initializeForNonStudio() {
    // Move the Android-related actions from the Tools menu into the Android submenu.
    ActionManager actionManager = ActionManager.getInstance();
    AnAction group = actionManager.getAction(GROUP_ANDROID_TOOLS);
    if (group instanceof ActionGroup) {
      ((ActionGroup)group).setPopup(true);
    }

    // Move the Android submenu to the end of the Tools menu.
    moveAction(GROUP_ANDROID_TOOLS, GROUP_TOOLS, GROUP_TOOLS, new Constraints(Anchor.LAST, null));

    // Move the "Sync Project with Gradle Files" from the File menu to Tools > Android.
    moveAction("Android.SyncProject", IdeActions.GROUP_FILE, GROUP_ANDROID_TOOLS, new Constraints(Anchor.FIRST, null));
    // Move the "Sync Project with Gradle Files" toolbar button to a less prominent place.
    moveAction("Android.MainToolBarGradleGroup", IdeActions.GROUP_MAIN_TOOLBAR, "Android.MainToolBarActionGroup",
               new Constraints(Anchor.LAST, null));
    UsageTracker.getInstance().setIdeBrand(AndroidStudioEvent.IdeBrand.INTELLIJ);
  }

  @Override
  public void disposeComponent() {
  }
}
