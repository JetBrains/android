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
package com.android.tools.idea.tests.gui.debugger;

import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.content.BaseLabel;
import com.intellij.openapi.wm.impl.content.ContentTabLabelFixture;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import java.util.ArrayList;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;
import java.util.List;
import java.util.regex.Pattern;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;

public class DebuggerTestBase {

  protected static final String DEBUG_CONFIG_NAME = "app";
  protected static final Pattern DEBUGGER_ATTACHED_PATTERN = Pattern.compile(".*Debugger attached to process.*", Pattern.DOTALL);
  protected static final long EMULATOR_LAUNCH_WAIT_SECONDS = 120;

  /**
   * Toggles breakpoints at {@code lines} of the source file {@code fileName}.
   */
  void openAndToggleBreakPoints(IdeFrameFixture ideFrame, String fileName, String... lines) {
    toggleBreakpoints(ideFrame.getEditor().open(fileName), lines);
  }

  void openAndToggleBreakPoints(@NotNull IdeFrameFixture ideFrameFixture, @NotNull VirtualFile file, @NotNull String... lines) {
    toggleBreakpoints(
      ideFrameFixture.getEditor().open(file, EditorFixture.Tab.DEFAULT),
      lines);
  }

  private void toggleBreakpoints(@NotNull EditorFixture editor, @NotNull String[] lines) {
    for (String line : lines) {
      editor.moveBetween("", line);
      editor.invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT);
    }
    editor.close();
  }

  void waitForSessionStart(DebugToolWindowFixture debugToolWindowFixture) {
    // Wait for "Debugger attached to process.*" to be printed on the app-native debug console.
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(DEBUG_CONFIG_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(DEBUGGER_ATTACHED_PATTERN), EMULATOR_LAUNCH_WAIT_SECONDS);
  }

  public static void checkAppIsPaused(IdeFrameFixture ideFrame, String[] expectedPattern) {
    checkAppIsPaused(ideFrame, expectedPattern, DEBUG_CONFIG_NAME);
  }

  public static void checkAppIsPaused(@NotNull IdeFrameFixture ideFrame, @NotNull String[] expectedPattern, @NotNull String debugConfigName) {
    ContentTabLabelFixture tabLabel = ContentTabLabelFixture.find(ideFrame.robot(), Matchers.byText(BaseLabel.class, debugConfigName));
    Wait.seconds(5).expecting("ContentTabLabel " + debugConfigName + " to become active")
      .until(() -> tabLabel.isSelected());

    Wait.seconds(5).expecting("variable patterns to match")
      .until(() -> verifyVariablesAtBreakpoint(ideFrame, expectedPattern, debugConfigName));
  }

  protected static void resume(@NotNull String debugConfigName, IdeFrameFixture ideFrame) {
    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(ideFrame);
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(debugConfigName);
    contentFixture.clickResumeButton();
  }

  private static boolean verifyVariablesAtBreakpoint(IdeFrameFixture ideFrame, String[] expectedVariablePatterns, String debugConfigName) {
    DebugToolWindowFixture debugToolWindowFixture = new DebugToolWindowFixture(ideFrame);
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(debugConfigName);

    Ref<XDebuggerTreeNode> debuggerTreeRoot = new Ref<>();
    Wait.seconds(5).expecting("debugger tree to appear").until(() -> {
      XDebuggerTreeNode root = contentFixture.getDebuggerTreeRoot();
      if (root != null) {
        debuggerTreeRoot.set(root);
        return true;
      } else {
        return false;
      }
    });

    List<String> unmatchedPatterns = getUnmatchedTerminalVariableValues(expectedVariablePatterns, debuggerTreeRoot.get());
    return unmatchedPatterns.isEmpty();
  }

  @NotNull
  public static String variableToSearchPattern(String name, String type, String value) {
    return String.format("%s = \\{%s\\} %s", name, type, value);
  }

  /**
   * Returns the appropriate pattern to look for a variable named {@code name} with the type {@code type} and value {@code value} appearing
   * in the Variables window in Android Studio.
   */
  @NotNull
  public static String variableToSearchPattern(String name, String value) {
    return String.format("%s = %s", name, value);
  }

  protected static void finishInstallUninstallProcess(@NotNull Robot robot) {
    MessagesFixture.findByTitle(robot, "Confirm Change").clickOk();
    DialogFixture downloadDialog = findDialog(withTitle("SDK Quickfix Installation"))
      .withTimeout(SECONDS.toMillis(30)).using(robot);
    JButtonFixture finish = downloadDialog.button(withText("Finish"));
    Wait.seconds(120)
      .expecting("Android source to be installed").until(finish::isEnabled);
    finish.click();
  }

  public static void stopDebugSession(DebugToolWindowFixture debugToolWindowFixture) {
    stopDebugSession(debugToolWindowFixture, DEBUG_CONFIG_NAME);
  }

  public static void stopDebugSession(DebugToolWindowFixture debugToolWindowFixture, String debugConfigName) {
    final ExecutionToolWindowFixture.ContentFixture contentFixture = debugToolWindowFixture.findContent(debugConfigName);
    contentFixture.waitForStopClick();
    contentFixture.waitForExecutionToFinish();
  }

  /**
   * Returns the subset of {@code expectedPatterns} which do not match any of the children (just the first level children, not recursive) of
   * {@code treeRoot} .
   */
  @NotNull
  private static List<String> getUnmatchedTerminalVariableValues(String[] expectedPatterns, XDebuggerTreeNode treeRoot) {
    String[] childrenTexts = debuggerTreeRootToChildrenTexts(treeRoot);
    List<String> unmatchedPatterns = new ArrayList<>();
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
}
