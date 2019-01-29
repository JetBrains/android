/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui;

import com.android.tools.idea.tests.gui.framework.GuiTestSuiteRunner;
import com.intellij.testGuiFramework.remote.server.JUnitServer;
import com.intellij.testGuiFramework.remote.server.JUnitServerHolder;
import org.junit.AfterClass;
import org.junit.runner.RunWith;

@RunWith(GuiTestSuiteRunner.class)
public class GuiTestSuite {
  @AfterClass
  public static void closeIdeFromRemoteRunner() {
    JUnitServer server = JUnitServerHolder.INSTANCE.getServerIfInitialized();
    if (server != null) {
      server.closeIdeAndStop();
    }
  }

}
