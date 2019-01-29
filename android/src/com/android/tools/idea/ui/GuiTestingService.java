/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.ui;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.GuardedBy;

public class GuiTestingService {
  public static final Key<Runnable> EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY = Key.create("gui.test.execute.before.build");
  public static final Key<String> GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY = Key.create("gui.test.gradle.build.output");

  private final Object LOCK = new Object();

  @GuardedBy("LOCK")
  private boolean myGuiTestingMode;

  @GuardedBy("LOCK")
  private GuiTestSuiteState myGuiTestSuiteState;

  public static GuiTestingService getInstance() {
    return ServiceManager.getService(GuiTestingService.class);
  }

  private GuiTestingService() {
    boolean en = false;

    ExtensionPointName<GuiTestingStatusProvider> epName = ExtensionPointName.create("com.android.tools.idea.ui.guiTestingStatusProvider");
    for (GuiTestingStatusProvider provider : epName.getExtensions()) {
      en = provider.enableUiTestMode() || en;
    }

    setGuiTestingMode(en);
  }

  public boolean isGuiTestingMode() {
    synchronized (LOCK) {
      return myGuiTestingMode;
    }
  }

  public void setGuiTestingMode(boolean guiTestingMode) {
    synchronized (LOCK) {
      myGuiTestingMode = guiTestingMode;
      myGuiTestSuiteState = myGuiTestingMode ? new GuiTestSuiteState() : null;
    }
  }

  // Ideally we would have this class in IdeTestApplication. The problem is that IdeTestApplication and UI tests run in different
  // ClassLoaders and UI tests are unable to see the same instance of IdeTestApplication.
  @NotNull
  public GuiTestSuiteState getGuiTestSuiteState() {
    synchronized (LOCK) {
      if (!myGuiTestingMode) {
        throw new UnsupportedOperationException("The method 'getGuiTestSuiteState' can only be invoked when running UI tests");
      }
      return myGuiTestSuiteState;
    }
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
