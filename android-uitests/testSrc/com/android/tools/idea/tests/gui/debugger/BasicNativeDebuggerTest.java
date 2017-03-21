/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.emulator.TestWithEmulator;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.ndk.MiscUtils;
import com.android.tools.idea.tests.util.NotMatchingPatternMatcher;
import com.google.common.collect.Lists;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.tree.TreeNode;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

@RunIn(TestGroup.QA)
@RunWith(GuiTestRunner.class)
public class BasicNativeDebuggerTest extends TestWithEmulator {

  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();

  private static final String DEBUG_CONFIG_NAME = "app";
  private static final Pattern DEBUGGER_ATTACHED_PATTERN = Pattern.compile(".*Debugger attached to process.*", Pattern.DOTALL);

  @Test
  public void testSessionRestart() throws IOException, ClassNotFoundException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("BasicJniApp");
    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    final IdeFrameFixture projectFrame = guiTest.ideFrame();

    // Setup breakpoints
    openAndToggleBreakPoints("app/src/main/jni/multifunction-jni.c", "return (*env)->NewStringUTF(env, message);");

    projectFrame.debugApp(DEBUG_CONFIG_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(projectFrame);
    waitForSessionStart(debugToolWindowFixture);

    projectFrame.findDebugApplicationButton().click();

    MessagesFixture errorMessage = MessagesFixture.findByTitle(guiTest.robot(), "Launching " + DEBUG_CONFIG_NAME);
    errorMessage.requireMessageContains("Restart App").click("Restart " + DEBUG_CONFIG_NAME);

    DeployTargetPickerDialogFixture deployTargetPicker = DeployTargetPickerDialogFixture.find(guiTest.robot());
    deployTargetPicker.selectDevice(AVD_NAME).clickOk();

    waitUntilDebugConsoleCleared(debugToolWindowFixture);
    waitForSessionStart(debugToolWindowFixture);
    stopDebugSession(debugToolWindowFixture);
  }

  @Test
  public void testMultiBreakAndResume() throws IOException, ClassNotFoundException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("BasicJniApp");
    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    final IdeFrameFixture projectFrame = guiTest.ideFrame();

    openAndToggleBreakPoints("app/src/main/jni/multifunction-jni.c",
                             "return sum;",
                             "return product;",
                             "return quotient;",
                             "return (*env)->NewStringUTF(env, message);"
    );

    // Setup the expected patterns to match the variable values displayed in Debug windows's 'Variables' tab.
    String[][] expectedPatterns = {
      {
        variableToSearchPattern("x1", "int", "1"),
        variableToSearchPattern("x2", "int", "2"),
        variableToSearchPattern("x3", "int", "3"),
        variableToSearchPattern("x4", "int", "4"),
        variableToSearchPattern("x5", "int", "5"),
        variableToSearchPattern("x6", "int", "6"),
        variableToSearchPattern("x7", "int", "7"),
        variableToSearchPattern("x8", "int", "8"),
        variableToSearchPattern("x9", "int", "9"),
        variableToSearchPattern("x10", "int", "10"),
        variableToSearchPattern("sum", "int", "55"),
      },
      {
        variableToSearchPattern("x1", "int", "1"),
        variableToSearchPattern("x2", "int", "2"),
        variableToSearchPattern("x3", "int", "3"),
        variableToSearchPattern("x4", "int", "4"),
        variableToSearchPattern("x5", "int", "5"),
        variableToSearchPattern("x6", "int", "6"),
        variableToSearchPattern("x7", "int", "7"),
        variableToSearchPattern("x8", "int", "8"),
        variableToSearchPattern("x9", "int", "9"),
        variableToSearchPattern("x10", "int", "10"),
        variableToSearchPattern("product", "int", "3628800"),
      },
      {
        variableToSearchPattern("x1", "int", "1024"),
        variableToSearchPattern("x2", "int", "2"),
        variableToSearchPattern("quotient", "int", "512"),
      },
      {
        variableToSearchPattern("sum_of_10_ints", "int", "55"),
        variableToSearchPattern("product_of_10_ints", "int", "3628800"),
        variableToSearchPattern("quotient", "int", "512")
      }
    };

    projectFrame.debugApp(DEBUG_CONFIG_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(projectFrame);
    waitForSessionStart(debugToolWindowFixture);

    checkBreakPointsAreHit(expectedPatterns);

    stopDebugSession(debugToolWindowFixture);
  }

  /**
   * Verifies that instant run hot swap works as expected on a C++ support project.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14603479
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicJniApp.
   *   2. Select auto debugger on Edit Configurations dialog.
   *   3. Set breakpoints both in Java and C++ code.
   *   4. Debug on a device running M or earlier.
   *   5. When the C++ breakpoint is hit, verify variables and resume
   *   6. When the Java breakpoint is hit, verify variables
   *   7. Stop debugging
   *   </pre>
   */
  @Test
  @Ignore
  public void testCAndJavaBreakAndResume() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("BasicJniApp");
    createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(guiTest.robot())
      .selectAutoDebugger()
      .clickOk();

    // Setup C++ and Java breakpoints.
    openAndToggleBreakPoints("app/src/main/jni/multifunction-jni.c", "return (*env)->NewStringUTF(env, message);");
    openAndToggleBreakPoints("app/src/main/java/com/example/BasicJniApp.java", "setContentView(tv);");

    // Setup the expected patterns to match the variable values displayed in Debug windows's 'Variables' tab.
    String[][] expectedPatterns = {
      {
        variableToSearchPattern("sum_of_10_ints", "int", "55"),
        variableToSearchPattern("product_of_10_ints", "int", "3628800"),
        variableToSearchPattern("quotient", "int", "512"),
      },
      {
        variableToSearchPattern("s", "\"Success. Sum = 55, Product = 3628800, Quotient = 512\""),
      },
    };

    ideFrameFixture.debugApp(DEBUG_CONFIG_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(ideFrameFixture);
    waitForSessionStart(debugToolWindowFixture);

    checkBreakPointsAreHit(expectedPatterns);
  }

  private void stopDebugSession(DebugToolWindowFixture debugToolWindowFixture) {
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
    contentFixture.waitForStopClick();
    contentFixture.waitForExecutionToFinish();
  }

  private void waitForSessionStart(DebugToolWindowFixture debugToolWindowFixture) {
    // Wait for "Debugger attached to process.*" to be printed on the app-native debug console.
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(DEBUGGER_ATTACHED_PATTERN), 70);
  }

  private void waitUntilDebugConsoleCleared(DebugToolWindowFixture debugToolWindowFixture) {
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
    contentFixture.waitForOutput(new NotMatchingPatternMatcher(DEBUGGER_ATTACHED_PATTERN), 10);
  }

  /////////////////////////////////////////////////////////////////
  ////     Methods to help control debugging under a test.  ///////
  /////////////////////////////////////////////////////////////////

  private void checkBreakPointsAreHit(String[][] expectedPatterns) {
    // Loop through all the breakpoints and match the strings printed in the Variables pane with the expected patterns
    for (String[] patterns : expectedPatterns) {
      Wait.seconds(5).expecting("the debugger tree to appear")
        .until(() -> verifyVariablesAtBreakpoint(patterns, DEBUG_CONFIG_NAME));

      MiscUtils.invokeMenuPathOnRobotIdle(guiTest.ideFrame(), "Run", "Resume Program");
    }
  }

  /**
   * Toggles breakpoints at {@code lines} of the source file {@code fileName}.
   */
  private void openAndToggleBreakPoints(String fileName, String... lines) {
    EditorFixture editor = guiTest.ideFrame().getEditor().open(fileName);
    for (String line : lines) {
      editor.moveBetween("", line);
      editor.invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT);
    }
  }

  @NotNull
  private static String[] debuggerTreeRootToChildrenTexts(XDebuggerTreeNode treeRoot) {
    List<? extends TreeNode> children = treeRoot.getChildren();
    String[] childrenTexts = new String[children.size()];
    int i = 0;
    for (TreeNode child : children) {
      childrenTexts[i] = ((XDebuggerTreeNode)child).getText().toString();
      ++i;
    }
    return childrenTexts;
  }

  /**
   * Returns the subset of {@code expectedPatterns} which do not match any of the children (just the first level children, not recursive) of
   * {@code treeRoot} .
   */
  @NotNull
  private static List<String> getUnmatchedTerminalVariableValues(String[] expectedPatterns, XDebuggerTreeNode treeRoot) {
    String[] childrenTexts = debuggerTreeRootToChildrenTexts(treeRoot);
    List<String> unmatchedPatterns = Lists.newArrayList();
    for (String expectedPattern : expectedPatterns) {
      boolean matched = false;
      for (String childText : childrenTexts) {
        if (childText.matches(expectedPattern)) {
          matched = true;
          break;
        }
      }
      if (!matched) {
        unmatchedPatterns.add(expectedPattern);
      }
    }
    return unmatchedPatterns;
  }

  /**
   * Returns the appropriate pattern to look for a variable named {@code name} with the type {@code type} and value {@code value} appearing
   * in the Variables window in Android Studio.
   */
  @NotNull
  private static String variableToSearchPattern(String name, String value) {
    return String.format("%s = %s", name, value);
  }

  @NotNull
  private static String variableToSearchPattern(String name, String type, String value) {
    return String.format("%s = \\{%s\\} %s", name, type, value);
  }

  private boolean verifyVariablesAtBreakpoint(String[] expectedVariablePatterns, String debugConfigName) {
    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(guiTest.ideFrame());
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(debugConfigName);

    contentFixture.clickDebuggerTreeRoot();
    Wait.seconds(5).expecting("debugger tree to appear").until(() -> contentFixture.getDebuggerTreeRoot() != null);

    // Get the debugger tree and print it.
    XDebuggerTreeNode debuggerTreeRoot = contentFixture.getDebuggerTreeRoot();
    if (debuggerTreeRoot == null) {
      return false;
    }

    List<String> unmatchedPatterns = getUnmatchedTerminalVariableValues(expectedVariablePatterns, debuggerTreeRoot);
    return unmatchedPatterns.isEmpty();
  }
}
