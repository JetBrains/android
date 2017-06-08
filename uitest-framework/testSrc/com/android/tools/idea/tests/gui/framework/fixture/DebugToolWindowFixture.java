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
import com.intellij.execution.ui.layout.impl.JBRunnerTabs;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.ui.content.Content;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.TabInfo;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.exception.ComponentLookupException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DebugToolWindowFixture extends ExecutionToolWindowFixture {
  public DebugToolWindowFixture(@NotNull IdeFrameFixture frameFixture) {
    super("Debug", frameFixture);
  }

  public DebugToolWindowFixture pressResumeProgram() {
    myRobot.click(findDebugResumeButton());
    return this;
  }

  public DebugToolWindowFixture waitForBreakPointHit() {
    findDebugResumeButton();
    return this;
  }

  @Nullable
  public Content getDebuggerContent(@NotNull String debugConfigName) {
    Content[] contents = getContents();
    for (Content content : contents) {
      if (debugConfigName.equals(content.getDisplayName())) {
        return content;
      }
    }
    return null;
  }

  public int getContentCount() {
    return getContents().length;
  }

  public boolean isTabSelected(@NotNull final String tabName) {
    try {
      JBTabs jbTabs = myRobot.finder().findByType(myToolWindow.getComponent(), JBRunnerTabs.class, true);
      TabInfo tabInfo = jbTabs.getSelectedInfo();
      if (tabInfo == null) {
        return false;
      }
      return tabName.equals(tabInfo.getText());
    } catch (ComponentLookupException e) {
      return false;
    }
  }

  private ActionButton findDebugResumeButton() {
    return GuiTests.waitUntilShowing(myRobot, new GenericTypeMatcher<ActionButton>(ActionButton.class) {
      @Override
      protected boolean isMatching(@NotNull ActionButton button) {
        return "com.intellij.xdebugger.impl.actions.ResumeAction".equals(button.getAction().getClass().getCanonicalName())
               && button.isEnabled();
      }
    });
  }
}
