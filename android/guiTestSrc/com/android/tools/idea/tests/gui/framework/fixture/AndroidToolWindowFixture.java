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

import com.android.ddmlib.Client;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.project.Project;
import com.intellij.ui.tabs.impl.TabLabel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.android.logcat.AndroidToolWindowFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.LONG_TIMEOUT;
import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.assertTrue;

public class AndroidToolWindowFixture extends ToolWindowFixture {
  private static class ProcessListFixture extends JListFixture {
    ProcessListFixture(@NotNull Robot robot, @NotNull JList target) {
      super(robot, target);
    }

    @NotNull
    public ProcessListFixture waitForProcess(@NotNull final String packageName) {
      pause(new Condition("Wait for the process list to show the package name.") {
        @Override
        public boolean test() {
          return GuiActionRunner.execute(new GuiQuery<Boolean>() {
            @Override
            protected Boolean executeInEDT() throws Throwable {
              for (int i = 0; i < target.getModel().getSize(); ++i) {
                String clientDescription = ((Client)target.getModel().getElementAt(i)).getClientData().getClientDescription();
                if (clientDescription != null && clientDescription.equals(packageName)) {
                  return true;
                }
              }
              return false;
            }
          });
        }
      }, LONG_TIMEOUT);
      return this;
    }

    @Override
    @NotNull
    public ProcessListFixture selectItem(@NotNull final String packageName) {
      clearSelection();
      Integer index = GuiActionRunner.execute(new GuiQuery<Integer>() {
        @Override
        protected Integer executeInEDT() throws Throwable {
          for (int i = 0; i < target.getModel().getSize(); ++i) {
            Client client = (Client)target.getModel().getElementAt(i);
            if (client.getClientData().getClientDescription() != null &&
                client.getClientData().getClientDescription().equals(packageName)) {
              return i;
            }
          }
          return -1;
        }
      });
      assertTrue(index >= 0);
      selectItem(index);
      return this;
    }
  }

  private ProcessListFixture myProcessListFixture;

  public AndroidToolWindowFixture(@NotNull Project project, final @NotNull Robot robot) {
    super(AndroidToolWindowFactory.TOOL_WINDOW_ID, project, robot);
    show();

    final JPanel contentPanel = getContentPanel();
    pause(new Condition("Wait for window to finish initializing.") {
      @Override
      public boolean test() {
        try {
          myRobot.finder().find(contentPanel, JLabelMatcher.withText("Initializing..."));
        }
        catch (ComponentLookupException e) {
          // Didn't find it. Great, we're done!
          return true;
        }
        return false;
      }
    }, SHORT_TIMEOUT);
    myProcessListFixture = new ProcessListFixture(robot, robot.finder().findByType(getContentPanel(), JList.class, false));
  }

  @NotNull
  public AndroidToolWindowFixture selectProcess(@NotNull String packageName) {
    myProcessListFixture.waitForProcess(packageName).selectItem(packageName);
    return this;
  }

  @NotNull
  public AndroidToolWindowFixture selectDevicesTab() {
    show();
    selectTab("Devices | logcat");
    return this;
  }

  public void clickTerminateApplication() {
    ActionButtonFixture action = findAction("Terminate Application");
    action.click();
  }

  private void selectTab(@NotNull final String tabName) {
    JBRunnerTabs tabs = myRobot.finder().findByType(getContentPanel(), JBRunnerTabs.class);
    TabLabel tabLabel = myRobot.finder().find(tabs, new GenericTypeMatcher<TabLabel>(TabLabel.class) {
      @Override
      protected boolean isMatching(TabLabel component) {
        return component.toString().equals(tabName);
      }
    });
    myRobot.click(tabLabel);
  }

  @NotNull
  private ActionButtonFixture findAction(@NotNull String text) {
    return ActionButtonFixture.findByText(text, myRobot, getContentPanel());
  }

  @NotNull
  private JPanel getContentPanel() {
    return (JPanel)myToolWindow.getContentManager().getComponent();
  }

  private void show() {
    activate();
    waitUntilIsVisible();
  }
}
