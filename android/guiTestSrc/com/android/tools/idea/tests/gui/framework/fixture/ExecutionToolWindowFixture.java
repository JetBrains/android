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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.layout.impl.GridImpl;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.ui.content.Content;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.util.ui.UIUtil;
import org.fest.reflect.core.Reflection;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.timing.Condition;
import org.fest.swing.timing.Timeout;
import org.fest.swing.util.TextMatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static junit.framework.Assert.assertNotNull;
import static org.fest.swing.timing.Pause.pause;

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

    public void waitForOutput(@NotNull final TextMatcher matcher, @NotNull Timeout timeout) {
      pause(new Condition("LogCat tool window output check for package name.") {
        @Override
        public boolean test() {
          return outputMatches(matcher);
        }
      }, timeout);
    }

    public boolean outputMatches(@NotNull TextMatcher matcher) {
      ConsoleViewImpl consoleView = getConsoleView();
      if (consoleView.getEditor() == null) {
        // If our handle has been replaced, find it again.
        getTabComponent("Console");
        consoleView = getConsoleView();
      }

      return matcher.isMatching(consoleView.getEditor().getDocument().getText());
    }

    @NotNull
    private ConsoleViewImpl getConsoleView() {
      return myRobot.finder().findByType(myContent.getComponent(), ConsoleViewImpl.class, false);
    }

    @NotNull
    public JComponent getTabComponent(@NotNull final String tabName) {
      return getTabContent(myParentToolWindow.myToolWindow.getComponent(), JBRunnerTabs.class, GridImpl.class, tabName);
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
        tabLabel = GuiTests.waitUntilFound(myRobot, new GenericTypeMatcher<TabLabel>(TabLabel.class) {
          @Override
          protected boolean isMatching(TabLabel component) {
            return component.toString().equals(tabName);
          }
        });
      }
      else {
        final JComponent parent = myRobot.finder().findByType(root, parentComponentType, false);
        tabLabel = GuiTests.waitUntilFound(myRobot, parent, new GenericTypeMatcher<TabLabel>(TabLabel.class) {
          @Override
          protected boolean isMatching(TabLabel component) {
            return component.getParent() == parent && component.toString().equals(tabName);
          }
        });
      }
      myRobot.click(tabLabel);
      return myRobot.finder().findByType(tabContentType);
    }

    public boolean isExecutionInProgress() {
      // Consider that execution is in progress if 'stop' toolbar button is enabled.
      ActionToolbarImpl toolbar = UIUtil.findComponentOfType(myContent.getComponent(), ActionToolbarImpl.class);
      assertNotNull(toolbar);
      List<ActionButton> buttons = UIUtil.findComponentsOfType(toolbar, ActionButton.class);
      for (ActionButton button : buttons) {
        // com.intellij.execution.actions is package private
        if (button.getAction().getClass().getSimpleName().equals("StopAction")) {
          return Reflection.method("isButtonEnabled").withReturnType(boolean.class).in(button).invoke();
        }
      }
      return true;
    }

    public boolean stop() {
      ActionToolbarImpl toolbar = UIUtil.findComponentOfType(myContent.getComponent(), ActionToolbarImpl.class);
      assertNotNull(toolbar);
      List<ActionButton> buttons = UIUtil.findComponentsOfType(toolbar, ActionButton.class);
      for (ActionButton button : buttons) {
        if (button.getAction() != null && button.getAction().getClass().getName().equals("com.intellij.execution.actions.StopAction")) {
          boolean enabled = Reflection.method("isButtonEnabled").withReturnType(boolean.class).in(button).invoke();
          if (enabled) {
            button.click();
            return true;
          }
          return false;
        }
      }
      return false;
    }
  }

  protected ExecutionToolWindowFixture(@NotNull String toolWindowId, @NotNull IdeFrameFixture ideFrame) {
    super(toolWindowId, ideFrame.getProject(), ideFrame.robot);
  }

  @NotNull
  public ContentFixture findContent(@NotNull String appName) {
    Content content = getContent(appName);
    assertNotNull(content);
    return new ContentFixture(this, myRobot, content);
  }
}
