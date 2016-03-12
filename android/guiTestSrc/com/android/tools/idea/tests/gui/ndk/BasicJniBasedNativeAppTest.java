/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.ndk;

import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.intellij.util.containers.HashMap;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;
import static org.junit.Assert.assertNotNull;

@BelongsToTestGroups({PROJECT_SUPPORT})
@RunWith(GuiTestRunner.class)
public class BasicJniBasedNativeAppTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String DEBUG_CONFIG_NAME = "app-native";

  /**
   * A test to import a simple jni application, install breakpoints, verify variable values and control execution values using lldb
   * debugger.
   *
   * Dependencies:
   *   - At least JDK 1.6.0_45
   *   - Nexus 5.something
   *   - Gradle 2.5
   *   - Ndk r10 or higher
   */
  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void testMultiBreakAndResume() throws IOException, ClassNotFoundException {
    int secondsToWait = 50;
    // Import the project and select the debug config 'app-native'.
    guiTest.importProjectAndWaitForProjectSyncToFinish("JniBasedBasicNdkApp", "2.5");
    final IdeFrameFixture projectFrame = guiTest.ideFrame();

    final int[] breakPoints = {35, 51, 58, 73};
    projectFrame.openAndToggleBreakPoints("app/src/main/jni/multifunction-jni.c", breakPoints);

    // Set up the expected patterns to match the variable values displayed in Debug windows's 'Variables' tab.
    final Map<Integer, String[]> breakpointToExpectedPatterns = new HashMap<Integer, String[]>();
    breakpointToExpectedPatterns.put(
      35, new String[] {
        IdeFrameFixture.variableToSearchPattern("x1", "int", "1"),
        IdeFrameFixture.variableToSearchPattern("x2", "int", "2"),
        IdeFrameFixture.variableToSearchPattern("x3", "int", "3"),
        IdeFrameFixture.variableToSearchPattern("x4", "int", "4"),
        IdeFrameFixture.variableToSearchPattern("x5", "int", "5"),
        IdeFrameFixture.variableToSearchPattern("x6", "int", "6"),
        IdeFrameFixture.variableToSearchPattern("x7", "int", "7"),
        IdeFrameFixture.variableToSearchPattern("x8", "int", "8"),
        IdeFrameFixture.variableToSearchPattern("x9", "int", "9"),
        IdeFrameFixture.variableToSearchPattern("x10", "int", "10"),
        IdeFrameFixture.variableToSearchPattern("sum", "int", "55")});
    breakpointToExpectedPatterns.put(
      51, new String[] {
        IdeFrameFixture.variableToSearchPattern("x1", "int", "1"),
        IdeFrameFixture.variableToSearchPattern("x2", "int", "2"),
        IdeFrameFixture.variableToSearchPattern("x3", "int", "3"),
        IdeFrameFixture.variableToSearchPattern("x4", "int", "4"),
        IdeFrameFixture.variableToSearchPattern("x5", "int", "5"),
        IdeFrameFixture.variableToSearchPattern("x6", "int", "6"),
        IdeFrameFixture.variableToSearchPattern("x7", "int", "7"),
        IdeFrameFixture.variableToSearchPattern("x8", "int", "8"),
        IdeFrameFixture.variableToSearchPattern("x9", "int", "9"),
        IdeFrameFixture.variableToSearchPattern("x10", "int", "10"),
        IdeFrameFixture.variableToSearchPattern("product", "int", "3628800")});
    breakpointToExpectedPatterns.put(
      58, new String[] {
        IdeFrameFixture.variableToSearchPattern("x1", "int", "1024"),
        IdeFrameFixture.variableToSearchPattern("x2", "int", "2"),
        IdeFrameFixture.variableToSearchPattern("quotient", "int", "512")});
    breakpointToExpectedPatterns.put(
      73, new String[] {
        IdeFrameFixture.variableToSearchPattern("sum_of_10_ints", "int", "55"),
        IdeFrameFixture.variableToSearchPattern("product_of_10_ints", "int", "3628800"),
        IdeFrameFixture.variableToSearchPattern("quotient", "int", "512")});

    // Launch a debug session and chooses the default emulator.
    projectFrame.debugApp(DEBUG_CONFIG_NAME);
    ChooseDeviceDialogFixture.find(guiTest.robot()).clickOk();

    // Wait for "Debugger attached to process.*" to be printed on the app-native debug console.
    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(projectFrame);
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
    assertNotNull(contentFixture);
    contentFixture.waitForOutput(new PatternTextMatcher(Pattern.compile(".*Debugger attached to process.*", Pattern.DOTALL)),
                                 secondsToWait);

    // Loop through all the breakpoints and match the strings printed in the Variables pane with the expected patterns setup in
    // breakpointToExpectedPatterns.
    for (int breakPoint : breakPoints) {
      final String[] expectedPatterns = breakpointToExpectedPatterns.get(breakPoint);
      Wait.seconds(secondsToWait).expecting("the debugger tree to appear").until(new Wait.Objective() {
        @Override
        public boolean isMet() {
          return projectFrame.verifyVariablesAtBreakpoint(expectedPatterns, DEBUG_CONFIG_NAME);
        }
      });

      projectFrame.resumeProgram();
    }
    projectFrame.getDebugToolWindow().findContent(DEBUG_CONFIG_NAME).stop();
    contentFixture.waitForOutput(new PatternTextMatcher(Pattern.compile(".*Process finished with exit code.*", Pattern.DOTALL)),
                                 secondsToWait);
  }
}
