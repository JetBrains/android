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

import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdEditWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.MockAvdManagerConnection;
import com.android.tools.idea.tests.gui.framework.ndk.MiscUtils;
import com.google.common.collect.Lists;
import com.intellij.util.containers.HashMap;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.runner.RunWith;

import javax.swing.tree.TreeNode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RunIn(TestGroup.QA)
@RunWith(GuiTestRunner.class)
public class BasicNativeDebuggerTest {

  @Rule public final NativeDebuggerGuiTestRule guiTest = new NativeDebuggerGuiTestRule();

  private static final String DEBUG_CONFIG_NAME = "app";
  private static final String AVD_NAME = "debugger_test_avd";

  @Before
  public void setUp() throws Exception {
    MockAvdManagerConnection.inject();
    getEmulatorConnection().deleteAvd(AVD_NAME);
  }

  @After
  public void tearDown() throws Exception {
    getEmulatorConnection().stopRunningAvd();
    getEmulatorConnection().deleteAvd(AVD_NAME);
  }

  @NotNull
  private static MockAvdManagerConnection getEmulatorConnection() {
    return (MockAvdManagerConnection)AvdManagerConnection.getDefaultAvdManagerConnection();
  }

  @Test
  public void testMultiBreakAndResume() throws IOException, ClassNotFoundException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("BasicJniApp");
    createAVD();
    final IdeFrameFixture projectFrame = guiTest.ideFrame();

    // Setup breakpoints
    final String[] breakPoints = {
      "return sum;",
      "return product;",
      "return quotient;",
      "return (*env)->NewStringUTF(env, message);",
    };
    openAndToggleBreakPoints("app/src/main/jni/multifunction-jni.c", breakPoints);

    // Setup the expected patterns to match the variable values displayed in Debug windows's 'Variables' tab.
    final Map<String, String[]> breakpointToExpectedPatterns = new HashMap<>();
    breakpointToExpectedPatterns.put(
      breakPoints[0], new String[] {
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
        variableToSearchPattern("sum", "int", "55")});
    breakpointToExpectedPatterns.put(
      breakPoints[1], new String[] {
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
        variableToSearchPattern("product", "int", "3628800")});
    breakpointToExpectedPatterns.put(
      breakPoints[2], new String[] {
        variableToSearchPattern("x1", "int", "1024"),
        variableToSearchPattern("x2", "int", "2"),
        variableToSearchPattern("quotient", "int", "512")});
    breakpointToExpectedPatterns.put(
      breakPoints[3], new String[] {
        variableToSearchPattern("sum_of_10_ints", "int", "55"),
        variableToSearchPattern("product_of_10_ints", "int", "3628800"),
        variableToSearchPattern("quotient", "int", "512")});

    projectFrame.debugApp(DEBUG_CONFIG_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    // Wait for "Debugger attached to process.*" to be printed on the app-native debug console.
    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(projectFrame);
    {
      final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
      contentFixture.waitForOutput(new PatternTextMatcher(Pattern.compile(".*Debugger attached to process.*", Pattern.DOTALL)), 50);
    }

    // Loop through all the breakpoints and match the strings printed in the Variables pane with the expected patterns setup in
    // breakpointToExpectedPatterns.
    for (int i = 0; i < breakPoints.length; ++i) {
      if (i > 0) {
        resumeProgram();
      }
      final String[] expectedPatterns = breakpointToExpectedPatterns.get(breakPoints[i]);
      Wait.seconds(1).expecting("the debugger tree to appear")
        .until(() -> verifyVariablesAtBreakpoint(expectedPatterns, DEBUG_CONFIG_NAME));
    }

    {
      // We cannot reuse the context fixture we got above, as its windows could have been repurposed for other things.
      final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
      contentFixture.stop();
      contentFixture.waitForExecutionToFinish();
    }
  }

  /////////////////////////////////////////////////////////////////
  ////     Methods to help control debugging under a test.  ///////
  /////////////////////////////////////////////////////////////////

  private void resumeProgram() {
    MiscUtils.invokeMenuPathOnRobotIdle(guiTest.ideFrame(), "Run", "Resume Program");
  }

  /**
   * Toggles breakpoints at {@code lines} of the source file {@code fileName}.
   */
  private void openAndToggleBreakPoints(String fileName, String[] lines) {
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
  private static String variableToSearchPattern(String name, String type, String value) {
    return String.format("%s = \\{%s\\} %s", name, type, value);
  }

  private boolean verifyVariablesAtBreakpoint(String[] expectedVariablePatterns, String debugConfigName) {
    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(guiTest.ideFrame());
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(debugConfigName);

    contentFixture.clickDebuggerTreeRoot();
    Wait.seconds(1).expecting("debugger tree to appear").until(() -> contentFixture.getDebuggerTreeRoot() != null);

    // Get the debugger tree and print it.
    XDebuggerTreeNode debuggerTreeRoot = contentFixture.getDebuggerTreeRoot();
    if (debuggerTreeRoot == null) {
      return false;
    }

    List<String> unmatchedPatterns = getUnmatchedTerminalVariableValues(expectedVariablePatterns, debuggerTreeRoot);
    return unmatchedPatterns.isEmpty();
  }

  private void createAVD() {
    AvdManagerDialogFixture avdManagerDialog = guiTest.ideFrame().invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();

    avdEditWizard.selectHardware()
      .selectHardwareProfile("Nexus 5X");
    avdEditWizard.clickNext();

    avdEditWizard.getChooseSystemImageStep()
      .selectTab("x86 Images")
      .selectSystemImage("Marshmallow", "23", "x86", "Android 6.0");
    avdEditWizard.clickNext();

    avdEditWizard.getConfigureAvdOptionsStep()
      .setAvdName(AVD_NAME)
      .showAdvancedSettings()
      .selectGraphicsSoftware();
    avdEditWizard.clickFinish();
    avdManagerDialog.close();
  }
}
