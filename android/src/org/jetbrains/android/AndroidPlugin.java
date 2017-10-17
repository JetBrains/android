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

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.fd.actions.HotswapAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.startup.Actions.moveAction;

/**
 * @author coyote
 */
public class AndroidPlugin implements ApplicationComponent {
  public static Key<Runnable> EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY = Key.create("gui.test.execute.before.build");
  public static Key<String> GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY = Key.create("gui.test.gradle.build.output");
  public static Key<String[]> GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY = Key.create("gradle.sync.command.line.options");
  private static final String GROUP_ANDROID_TOOLS = "AndroidToolsGroup";
  private static final String GROUP_TOOLS = "ToolsMenu";

  private static boolean ourGuiTestingMode;
  private static GuiTestSuiteState ourGuiTestSuiteState;

  @Override
  @NotNull
  public String getComponentName() {
    return "AndroidApplicationComponent";
  }

  @Override
  public void initComponent() {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      initializeForStudio();
    } else {
      initializeForNonStudio();
    }
  }

  /**
   * Initializes the Android plug-in when it runs as part of Android Studio.
   */
  private static void initializeForStudio() {
    // Since the executor actions are registered dynamically, and we want to insert ourselves in the middle, we have to do this
    // in code as well (instead of xml).
    ActionManager actionManager = ActionManager.getInstance();
    AnAction runnerActions = actionManager.getAction(IdeActions.GROUP_RUNNER_ACTIONS);
    if (runnerActions instanceof DefaultActionGroup) {
      ((DefaultActionGroup)runnerActions).add(new HotswapAction(), new Constraints(Anchor.AFTER, IdeActions.ACTION_DEFAULT_RUNNER));
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

  public static boolean isGuiTestingMode() {
    return ourGuiTestingMode;
  }

  public static void setGuiTestingMode(boolean guiTestingMode) {
    ourGuiTestingMode = guiTestingMode;
    ourGuiTestSuiteState = ourGuiTestingMode ? new GuiTestSuiteState() : null;
  }

  // Ideally we would have this class in IdeTestApplication. The problem is that IdeTestApplication and UI tests run in different
  // ClassLoaders and UI tests are unable to see the same instance of IdeTestApplication.
  @NotNull
  public static GuiTestSuiteState getGuiTestSuiteState() {
    if (!ourGuiTestingMode) {
      throw new UnsupportedOperationException("The method 'getGuiTestSuiteState' can only be invoked when running UI tests");
    }
    return ourGuiTestSuiteState;
  }

  public static class GuiTestSuiteState {
    private boolean mySkipSdkMerge;

    public boolean isSkipSdkMerge() {
      return mySkipSdkMerge;
    }

    public void setSkipSdkMerge(boolean skipSdkMerge) {
      mySkipSdkMerge = skipSdkMerge;
    }
  }
}
