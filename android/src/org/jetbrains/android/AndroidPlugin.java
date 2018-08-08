// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android;

import com.android.tools.idea.IdeInfo;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.BaseComponent;

import static com.android.tools.idea.startup.Actions.moveAction;

public class AndroidPlugin implements BaseComponent {
  private static final String GROUP_ANDROID_TOOLS = "AndroidToolsGroup";
  private static final String GROUP_TOOLS = "ToolsMenu";

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
  }

  @Override
  public void disposeComponent() {
  }
}
