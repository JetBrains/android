// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.tests.gui.java8;

import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RunWith(GuiTestRunner.class)
public class CompileWithJava8Test {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String CONF_NAME = "app";

  /**
   * Verifies that Compile a project with Java 8.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: d6dc23f3-33ff-4ffc-af80-6ab822388274
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import MinSdk24App project, in this project, build.gradle(Module:app) is enabled
   *      Java 8 for lambda expressions; and In MainActivity.java, the following statement
   *      is added to onCreate() method:
   *      new Thread(() ->{
   *          Log.d("TAG", "Hello World from Lambda Expression");
   *      }).start();
   *   2. Create an emulator.
   *   3. Run Build -> Rebuild Project (Project should build successfully).
   *   4. Run this activity on emulator.
   *   Verify:
   *   1. Verify if statement prints "D/TAG: Hello World from Lambda Expression" on logcat.
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // b/70227905
  public void compileWithJave8() throws Exception {
    IdeFrameFixture ideFrameFixture =
      guiTest.importProjectAndWaitForProjectSyncToFinish("MinSdk24App");

    // Test will fail here, relative bug: b/70227905
    ideFrameFixture.invokeMenuPath("Build", "Rebuild Project").waitForBuildToFinish(BuildMode.REBUILD);

    emulator.createDefaultAVD(ideFrameFixture.invokeAvdManager());

    ideFrameFixture.runApp(CONF_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    Pattern CONNECTED_APP_PATTERN = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);
    ExecutionToolWindowFixture.ContentFixture runWindow = ideFrameFixture.getRunToolWindow().findContent(CONF_NAME);
    runWindow.waitForOutput(new PatternTextMatcher(CONNECTED_APP_PATTERN), TimeUnit.MINUTES.toSeconds(2));

    // TODO: Verify statement prints "D/TAG: Hello World from Lambda Expression" on logcat.
  }
}
