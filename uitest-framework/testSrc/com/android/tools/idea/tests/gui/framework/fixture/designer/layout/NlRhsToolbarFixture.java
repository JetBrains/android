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
package com.android.tools.idea.tests.gui.framework.fixture.designer.layout;

import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.common.actions.IssueNotificationAction;
import com.android.tools.idea.uibuilder.error.IssuePanel;
import com.android.tools.idea.uibuilder.surface.PanZoomPanel;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;

/**
 * Fixture representing the configuration toolbar above an associated layout editor
 */
public class NlRhsToolbarFixture {

  @NotNull private final NlEditorFixture myNlEditorFixture;
  @NotNull private final ActionToolbar myToolBar;

  public NlRhsToolbarFixture(@NotNull NlEditorFixture nlEditorFixture, @NotNull ActionToolbar toolbar) {
    myNlEditorFixture = nlEditorFixture;
    myToolBar = toolbar;
  }

  public void openPanZoomPanel() {
    Robot robot = myNlEditorFixture.robot();
    ActionButton button = waitUntilShowing(
      robot, myToolBar.getComponent(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
        @Override
        protected boolean isMatching(@NotNull ActionButton component) {
          String text = component.getAction().getTemplatePresentation().getText();
          return text != null && text.contains("Pan and Zoom");
        }
      });
    new ActionButtonFixture(robot, button).click();
    waitUntilShowing(robot, Matchers.byType(PanZoomPanel.class)); // don't return a fixture; just make sure this shows
  }

  public void openIssuePanel() {
    Robot robot = myNlEditorFixture.robot();
    ActionButton button = waitUntilShowing(
      robot, myToolBar.getComponent(), new GenericTypeMatcher<ActionButton>(ActionButton.class) {
        @Override
        protected boolean isMatching(@NotNull ActionButton component) {
          String text = component.getAction().getTemplatePresentation().getText();
          return text != null && (text.equals(IssueNotificationAction.SHOW_ISSUE)
                                  || text.equals(IssueNotificationAction.NO_ISSUE));
        }
      });
    new ActionButtonFixture(robot, button).click();
    waitUntilShowing(robot, Matchers.byType(IssuePanel.class));
  }
}
