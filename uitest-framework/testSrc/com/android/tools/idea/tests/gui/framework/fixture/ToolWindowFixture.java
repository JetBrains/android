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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.util.ui.UIUtil;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.TextMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public abstract class ToolWindowFixture {

  private static final int SECONDS_TO_WAIT = 120;

  @NotNull protected final String myToolWindowId;
  @NotNull protected final Project myProject;
  @NotNull protected final Robot myRobot;
  @NotNull protected final ToolWindow myToolWindow;

  protected ToolWindowFixture(@NotNull final String toolWindowId, @NotNull final Project project, @NotNull Robot robot) {
    myToolWindowId = toolWindowId;
    myProject = project;
    final Ref<ToolWindow> toolWindowRef = new Ref<>();
    Wait.seconds(SECONDS_TO_WAIT).expecting("tool window with ID '" + toolWindowId + "' to be found")
      .until(() -> {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId);
        toolWindowRef.set(toolWindow);
        return toolWindow != null;
      });
    myRobot = robot;
    myToolWindow = toolWindowRef.get();
  }

  @Nullable
  protected Content getContent(@NotNull final String displayName) {
    activateAndWaitUntilIsVisible();
    final Ref<Content> contentRef = new Ref<>();
    Wait.seconds(SECONDS_TO_WAIT).expecting("content '" + displayName + "' to be found")
      .until(() -> {
        Content[] contents = getContents();
        for (Content content : contents) {
          if (displayName.equals(content.getDisplayName())) {
            contentRef.set(content);
            return true;
          }
        }
        return false;
      });
    return contentRef.get();
  }

  @Nullable
  protected Content getContent(@NotNull final TextMatcher displayNameMatcher) {
    long startTime = System.currentTimeMillis();
    activateAndWaitUntilIsVisible(SECONDS_TO_WAIT);
    long secondsRemaining = SECONDS_TO_WAIT - TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime);
    final Ref<Content> contentRef = new Ref<>();
    Wait.seconds(secondsRemaining).expecting("content matching " + displayNameMatcher.formattedValues() + " to be found")
      .until(() -> {
        Content[] contents = getContents();
        for (Content content : contents) {
          String displayName = content.getDisplayName();
          if (displayNameMatcher.isMatching(displayName)) {
            contentRef.set(content);
            return true;
          }
        }
        return false;
      });
    return contentRef.get();
  }

  protected final void activateAndWaitUntilIsVisible() {
    activateAndWaitUntilIsVisible(SECONDS_TO_WAIT);
  }

  protected final void activateAndWaitUntilIsVisible(long secondsToWait) {
    long startTime = System.currentTimeMillis();
    activate();
    long secondsRemaining = secondsToWait - TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - startTime);
    waitUntilIsVisible(secondsRemaining);
  }

  @NotNull
  protected Content[] getContents() {
    return myToolWindow.getContentManager().getContents();
  }

  protected boolean isActive() {
    return GuiQuery.getNonNull(() -> {
      Content content = myToolWindow.getContentManager().getSelectedContent();
      Component owner = IdeFocusManager.getInstance(myProject).getFocusOwner();
      return myToolWindow.isActive() && content != null && UIUtil.isDescendingFrom(owner, content.getPreferredFocusableComponent());
    });
  }

  public void activate() {
    Wait.seconds(SECONDS_TO_WAIT).expecting("ToolWindow '" + myToolWindowId + "' to be activated").until(() -> {
      boolean isActive = isActive();
      if (!isActive) {
        GuiTask.execute(() -> myToolWindow.activate(null));
      }
      return isActive;
    });
  }

  protected void waitUntilIsVisible() {
    waitUntilIsVisible(30);
  }

  protected void waitUntilIsVisible(long secondsToWait) {
    Wait.seconds(secondsToWait).expecting("ToolWindow '" + myToolWindowId + "' to be visible")
      .until(() -> {
        if (!isActive()) {
          activate();
        }
        return GuiQuery.getNonNull(
          () -> myToolWindow.isVisible() && myToolWindow.getComponent().isVisible() && myToolWindow.getComponent().isShowing());
      });
  }
}
