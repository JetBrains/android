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

import com.android.annotations.concurrency.GuardedBy;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.Key;

public class GuiTestingService {
  public static final Key<Runnable> EXECUTE_BEFORE_PROJECT_BUILD_IN_GUI_TEST_KEY = Key.create("gui.test.execute.before.build");
  public static final Key<String> GRADLE_BUILD_OUTPUT_IN_GUI_TEST_KEY = Key.create("gui.test.gradle.build.output");

  private final Object LOCK = new Object();

  @GuardedBy("LOCK")
  private boolean myGuiTestingMode;

  public static GuiTestingService getInstance() {
    return ApplicationManager.getApplication().getService(GuiTestingService.class);
  }

  public static GuiTestingService getInstanceIfCreated() {
    return ApplicationManager.getApplication().getServiceIfCreated(GuiTestingService.class);
  }

  private GuiTestingService() {
    ExtensionPointName<GuiTestingStatusProvider> epName = ExtensionPointName.create("com.android.tools.idea.ui.guiTestingStatusProvider");
    for (GuiTestingStatusProvider provider : epName.getExtensions()) {
      if (provider.enableUiTestMode()) {
        setGuiTestingMode(true);
        break;
      }
    }
  }

  public boolean isGuiTestingMode() {
    synchronized (LOCK) {
      return myGuiTestingMode;
    }
  }

  public void setGuiTestingMode(boolean guiTestingMode) {
    synchronized (LOCK) {
      myGuiTestingMode = guiTestingMode;
    }
  }

  public static boolean isInTestingMode() {
    GuiTestingService guiTestingService = getInstance();
    return (guiTestingService != null && guiTestingService.isGuiTestingMode()) || ApplicationManager.getApplication().isUnitTestMode();
  }
}
