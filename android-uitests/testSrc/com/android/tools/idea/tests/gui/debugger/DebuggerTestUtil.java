/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.debugger;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditConfigurationsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

public class DebuggerTestUtil {

  public static final String AUTO = "Auto";
  public static final String DUAL = "Dual";
  public static final String NATIVE = "Native";
  public static final String JAVA = "Java";
  public static final String JAVA_DEBUGGER_CONF_NAME = "app-java";

  public static void setDebuggerType(@NotNull IdeFrameFixture ideFrameFixture,
                       @NotNull String type) {
    ideFrameFixture.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(ideFrameFixture.robot())
      .selectDebuggerType(type)
      .clickOk();
  }

  public static DebugToolWindowFixture debugAppAndWaitForSessionToStart(@NotNull IdeFrameFixture ideFrameFixture,
                                                                        @NotNull GuiTestRule guiTest,
                                                                        @NotNull String configName,
                                                                        @NotNull String avdName) {
    return debugAppAndWaitForSessionToStart(ideFrameFixture, guiTest, configName, avdName, Wait.seconds(90));
  }

  public static DebugToolWindowFixture debugAppAndWaitForSessionToStart(@NotNull IdeFrameFixture ideFrameFixture,
                                                                        @NotNull GuiTestRule guiTest,
                                                                        @NotNull String configName,
                                                                        @NotNull String avdName,
                                                                        @NotNull Wait wait) {
    ideFrameFixture.debugApp(configName)
      .selectDevice(avdName)
      .clickOk();

    // Wait for background tasks to finish before requesting Debug Tool Window. Otherwise Debug Tool Window won't activate.
    GuiTests.waitForBackgroundTasks(guiTest.robot(), wait);

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(ideFrameFixture);

    // Wait for "Debugger attached to process.*" to be printed on the app-native debug console.
    ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(configName);
    Pattern DEBUGGER_ATTACHED_PATTERN = Pattern.compile(".*Debugger attached to process.*", Pattern.DOTALL);
    contentFixture.waitForOutput(new PatternTextMatcher(DEBUGGER_ATTACHED_PATTERN), 120);

    return debugToolWindowFixture;
  }

}
