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
import com.android.tools.idea.monitor.AndroidToolWindowFactory;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.project.Project;
import com.intellij.ui.tabs.impl.TabLabel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JListFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.assertNotNull;

public class AndroidToolWindowFixture extends ToolWindowFixture {
  @NotNull private final ProcessListFixture myProcessListFixture;

  public AndroidToolWindowFixture(@NotNull Project project, final @NotNull Robot robot) {
    super(AndroidToolWindowFactory.TOOL_WINDOW_ID, project, robot);
    show();

    final JPanel contentPanel = getContentPanel();
    Wait.minutes(2).expecting("window to finish initializing").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        try {
          myRobot.finder().find(contentPanel, JLabelMatcher.withText("Initializing..."));
        }
        catch (ComponentLookupException e) {
          // Didn't find it. Great, we're done!
          return true;
        }
        return false;
      }
    });
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
      protected boolean isMatching(@NotNull TabLabel component) {
        return tabName.equals(component.toString());
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

  private static class ProcessListFixture extends JListFixture {
    ProcessListFixture(@NotNull Robot robot, @NotNull JList target) {
      super(robot, target);
    }

    @NotNull
    public ProcessListFixture waitForProcess(@NotNull final String packageName) {
      Wait.minutes(2).expecting("the process list to show the package name").until(new Wait.Objective() {
        @Override
        public boolean isMet() {
          //noinspection ConstantConditions
          return execute(new GuiQuery<Boolean>() {
            @Override
            protected Boolean executeInEDT() throws Throwable {
              ListModel model = target().getModel();
              int size = model.getSize();
              for (int i = 0; i < size; ++i) {
                Client client = (Client)model.getElementAt(i);
                String clientDescription = client.getClientData().getClientDescription();
                if (packageName.equals(clientDescription)) {
                  return true;
                }
              }
              return false;
            }
          });
        }
      });
      return this;
    }

    @Override
    @NotNull
    public ProcessListFixture selectItem(@Nullable final String packageName) {
      clearSelection();
      assertNotNull(packageName);
      Integer index = execute(new GuiQuery<Integer>() {
        @Override
        protected Integer executeInEDT() throws Throwable {
          for (int i = 0; i < target().getModel().getSize(); ++i) {
            Client client = (Client)target().getModel().getElementAt(i);
            if (packageName.equals(client.getClientData().getClientDescription())) {
              return i;
            }
          }
          return -1;
        }
      });
      assertNotNull(index);
      assertThat(index).isAtLeast(0);
      selectItem(index);
      return this;
    }
  }
}
