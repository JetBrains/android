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

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * @author coyote
 */
public class AndroidPlugin implements ApplicationComponent {
  public static Key<Runnable> EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY = Key.create("gui.test.execute.before.build");
  public static Key<Runnable> EXECUTE_BEFORE_PROJECT_SYNC_TASK_IN_GUI_TEST_KEY = Key.create("gui.test.execute.before.sync.task");
  public static Key<String> GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY = Key.create("gui.test.gradle.build.output");
  public static Key<String[]> GRADLE_SYNC_COMMAND_LINE_OPTIONS_KEY = Key.create("gradle.sync.command.line.options");

  private static boolean ourGuiTestingMode;
  private static GuiTestSuiteState ourGuiTestSuiteState;

  @Override
  @NotNull
  public String getComponentName() {
    return "AndroidApplicationComponent";
  }

  @Override
  public void initComponent() {
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
    private boolean myUseCachedGradleModelOnly;

    public boolean isSkipSdkMerge() {
      return mySkipSdkMerge;
    }

    public void setSkipSdkMerge(boolean skipSdkMerge) {
      mySkipSdkMerge = skipSdkMerge;
    }

    public boolean syncWithCachedModelOnly() {
      return myUseCachedGradleModelOnly;
    }

    public void setUseCachedGradleModelOnly(boolean useCachedGradleModelOnly) {
      myUseCachedGradleModelOnly = useCachedGradleModelOnly;
    }
  }
}
