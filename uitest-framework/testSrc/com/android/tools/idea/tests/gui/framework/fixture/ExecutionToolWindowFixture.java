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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.annotations.Nullable;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.ui.layout.impl.GridImpl;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionMenuItem;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.ui.content.Content;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.TextMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.ui.UIUtil.findComponentOfType;
import static com.intellij.util.ui.UIUtil.findComponentsOfType;
import static org.fest.reflect.core.Reflection.method;

public class ExecutionToolWindowFixture extends ToolWindowFixture {
  public static class ContentFixture {
    @NotNull private final ExecutionToolWindowFixture myParentToolWindow;
    @NotNull private final Robot myRobot;
    @NotNull private final Content myContent;

    private ContentFixture(@NotNull ExecutionToolWindowFixture parentToolWindow, @NotNull Robot robot, @NotNull Content content) {
      myParentToolWindow = parentToolWindow;
      myRobot = robot;
      myContent = content;
    }

    public void waitForOutput(@NotNull final TextMatcher matcher, long secondsToWait) {
      Wait.seconds(secondsToWait).expecting("LogCat tool window output check for package name").until(() -> outputMatches(matcher));
    }

    /**
     * Waits until it grabs the console window and then returns true if its text matches that of {@code matcher}.
     * Note: This method may not terminate if the console view cannot be found.
     */
    public boolean outputMatches(@NotNull TextMatcher matcher) {
      return matcher.isMatching(getOutput());
    }

    @NotNull
    public String getOutput() {
      ConsoleViewImpl consoleView;
      while ((consoleView = findConsoleView()) == null || consoleView.getEditor() == null) {
        // If our handle has been replaced, find it again.
        JComponent consoleComponent = getTabComponent("Console");
        myRobot.click(consoleComponent);
      }
      return consoleView.getEditor().getDocument().getText();
    }

    // Returns the console or null if it is not found.
    @Nullable
    private ConsoleViewImpl findConsoleView() {
      try {
        return myRobot.finder().findByType(myParentToolWindow.myToolWindow.getComponent(), ConsoleViewImpl.class, false);
      } catch (ComponentLookupException e) {
        return null;
      }
    }

    @NotNull
    public JComponent getTabComponent(@NotNull final String tabName) {
      return getTabContent(myParentToolWindow.myToolWindow.getComponent(), JBRunnerTabs.class, GridImpl.class, tabName);
    }

    @NotNull
    public UnitTestTreeFixture getUnitTestTree() {
      return new UnitTestTreeFixture(this, myRobot.finder().findByType(myContent.getComponent(), TestTreeView.class));
    }

    // Returns the debugger tree or null.
    @Nullable
    public XDebuggerTree getDebuggerTree() {
      try {
        JComponent debuggerComponent = getTabComponent("Debugger");
        if (debuggerComponent != null) {
          myRobot.click(debuggerComponent);
        }
        ThreeComponentsSplitter threeComponentsSplitter =
            myRobot.finder().findByType(debuggerComponent, ThreeComponentsSplitter.class, false);
        JComponent innerComponent = threeComponentsSplitter.getInnerComponent();
        return myRobot.finder().findByType(innerComponent, XDebuggerTree.class, false);
      } catch (ComponentLookupException | IllegalComponentStateException e) {
        return null;
      }
    }

    // Returns the root of the debugger tree or null.
    @Nullable
    public XDebuggerTreeNode getDebuggerTreeRoot() {
      XDebuggerTree debuggerTree = getDebuggerTree();
      if (debuggerTree == null) {
        return null;
      }
      return debuggerTree.getRoot();
    }

    @NotNull
    public JListFixture getFramesListFixture() {
      Wait.seconds(5).expecting("Frames list present").until(() -> getFramesList() != null);
      return new JListFixture(myRobot, getFramesList());
    }

    @Nullable
    private XDebuggerFramesList getFramesList() {
      try {
        JComponent debuggerComponent = getTabComponent("Debugger");
        myRobot.click(debuggerComponent);
        ThreeComponentsSplitter splitter =
          myRobot.finder().findByType(debuggerComponent, ThreeComponentsSplitter.class, false);
        JComponent component = splitter.getFirstComponent();
        return myRobot.finder().findByType(component, XDebuggerFramesList.class, false);
      } catch (ComponentLookupException | IllegalComponentStateException e) {
        return null;
      }
    }

    public void clickDebuggerTreeRoot() {
      try {
        JComponent debuggerComponent = getTabComponent("Debugger");
        myRobot.click(debuggerComponent);
      } catch (ComponentLookupException e) { }
    }

    public JBPopupMenu rightClickVariableInDebuggerVariables(@NotNull IdeFrameFixture ideFrame, @NotNull String variableName) {
      Wait.seconds(5).expecting("Debugger tree present").until(() -> getDebuggerTree() != null);
      JTree tree = (JTree)getDebuggerTree();

      JTreeFixture jTreeFixture = new JTreeFixture(myRobot, tree);
      int index = -1;
      int rowCount = tree.getRowCount();
      for (int i = 0; i < rowCount; i++) {
        String value = jTreeFixture.valueAt(i);
        if (variableName.equals(value)) {
          index = i;
          break;
        }
      }
      assertThat(index > -1).isTrue();

      Wait.seconds(5).expecting("debugger tree to be enabled").until(() -> tree.isEnabled());
      jTreeFixture.rightClickRow(index);
      return GuiTests.waitUntilShowingAndEnabled(myRobot, ideFrame.target(), Matchers.byType(JBPopupMenu.class));
    }

    public void addWatchpoint(@NotNull IdeFrameFixture ideFrame, @Nullable Container popupMenu) {
      Component addWatchpoint = GuiTests.waitUntilShowingAndEnabled(
          myRobot,
          popupMenu,
          Matchers.byText(ActionMenuItem.class, "Add Watchpoint"));
      myRobot.click(addWatchpoint);

      //TODO: Need to a new fixture for watchpoint configuration dialog.
      //Here, we directly click on "Done" button to finish creating an default one with write type.
      Component doneButton = GuiTests.waitUntilShowingAndEnabled(myRobot, ideFrame.target(), Matchers.byText(JButton.class, "Done"));
      myRobot.click(doneButton);
    }

    @NotNull
    private JComponent getTabContent(@NotNull final JComponent root,
                                     final Class<? extends JBTabsImpl> parentComponentType,
                                     @NotNull final Class<? extends JComponent> tabContentType,
                                     @NotNull final String tabName) {
      myParentToolWindow.activate();
      myParentToolWindow.waitUntilIsVisible();

      TabLabel tabLabel;
      if (parentComponentType == null) {
        tabLabel = waitUntilShowing(myRobot, new GenericTypeMatcher<TabLabel>(TabLabel.class) {
          @Override
          protected boolean isMatching(@NotNull TabLabel component) {
            return component.toString().equals(tabName);
          }
        });
      }
      else {
        final JComponent parent = myRobot.finder().findByType(root, parentComponentType, false);
        tabLabel = waitUntilShowing(myRobot, parent, new GenericTypeMatcher<TabLabel>(TabLabel.class) {
          @Override
          protected boolean isMatching(@NotNull TabLabel component) {
            return component.getParent() == parent && component.toString().equals(tabName);
          }
        });
      }
      assertThat(tabLabel.isShowing()).isTrue();
      myRobot.click(tabLabel);
      return myRobot.finder().findByType(tabContentType);
    }

    public boolean isExecutionInProgress() {
      // Consider that execution is in progress if 'stop' toolbar button is enabled.
      for (ActionButton button : getToolbarButtons()) {
        if ("com.intellij.execution.actions.StopAction".equals(button.getAction().getClass().getCanonicalName())) {
          //noinspection ConstantConditions
          return method("isButtonEnabled").withReturnType(boolean.class).in(button).invoke();
        }
      }
      return true;
    }

    public void rerun() {
      for (ActionButton button : getToolbarButtons()) {
        if ("com.intellij.execution.runners.FakeRerunAction".equals(button.getAction().getClass().getCanonicalName())) {
          myRobot.click(button);
          return;
        }
      }

      throw new IllegalStateException("Could not find the Re-run button.");
    }

    public void rerunFailed() {
      for (ActionButton button : getToolbarButtons()) {
        if ("com.intellij.execution.junit2.ui.actions.RerunFailedTestsAction".equals(button.getAction().getClass().getCanonicalName())) {
          myRobot.click(button);
          return;
        }
      }

      throw new IllegalStateException("Could not find the Re-run failed tests button.");
    }

    public void waitForExecutionToFinish() {
      Wait.seconds(10).expecting("execution to finish").until(() -> !isExecutionInProgress());
    }

    @TestOnly
    public boolean stop() {
      for (final ActionButton button : getToolbarButtons()) {
        final AnAction action = button.getAction();
        if (action != null && action.getClass().getName().equals("com.intellij.execution.actions.StopAction")) {
          //noinspection ConstantConditions
          boolean enabled = method("isButtonEnabled").withReturnType(boolean.class).in(button).invoke();
          if (enabled) {
            GuiTask.execute(() -> button.click());
            return true;
          }
          return false;
        }
      }
      return false;
    }

    public void waitForStopClick() {
      Wait.seconds(5).expecting("Stop button clicked").until(() -> {
        for (ActionButton button : getToolbarButtons()) {
          if (button.getAction() == null || !button.getAction().getClass().getName().equals("com.intellij.execution.actions.StopAction")) {
            continue;
          }
          if (button.isEnabled() && button.isShowing()) {
            GuiTask.execute(() -> button.click());
            return true;
          }
        }
        return false;
      });
    }

    @NotNull
    private List<ActionButton> getToolbarButtons() {
      ActionToolbarImpl toolbar = findComponentOfType(myContent.getComponent(), ActionToolbarImpl.class);
      assert toolbar != null;
      return findComponentsOfType(toolbar, ActionButton.class);
    }
  }  // End class ContentFixture

  protected ExecutionToolWindowFixture(@NotNull String toolWindowId, @NotNull IdeFrameFixture ideFrame) {
    super(toolWindowId, ideFrame.getProject(), ideFrame.robot());
  }

  @NotNull
  public ContentFixture findContent(@NotNull String tabName) {
    return new ContentFixture(this, myRobot, getContent(tabName));
  }

  @NotNull
  public ContentFixture findContent(@NotNull TextMatcher tabNameMatcher) {
    return new ContentFixture(this, myRobot, getContent(tabNameMatcher));
  }

  @NotNull
  private JPanel getContentPanel() {
    return (JPanel)myToolWindow.getContentManager().getComponent();
  }

  @NotNull
  private ActionButtonFixture findAction(@NotNull String text) {
    return ActionButtonFixture.findByText(text, myRobot, getContentPanel());
  }

  @NotNull
  public DeployTargetPickerDialogFixture clickRerunApplication() {
    findAction("Rerun").waitUntilEnabledAndShowing().click();
    return DeployTargetPickerDialogFixture.find(myRobot);
  }
}
