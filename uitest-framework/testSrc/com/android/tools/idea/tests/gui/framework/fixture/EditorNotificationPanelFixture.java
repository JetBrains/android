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

import com.android.tools.idea.tests.gui.framework.Wait;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.HyperlinkLabel;
import org.fest.reflect.core.Reflection;
import org.fest.swing.core.GenericTypeMatcher;
import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Preconditions.checkState;

public class EditorNotificationPanelFixture extends JComponentFixture<EditorNotificationPanelFixture, EditorNotificationPanel> {

  private final IdeFrameFixture myIdeFrameFixture;

  EditorNotificationPanelFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull EditorNotificationPanel target) {
    super(EditorNotificationPanelFixture.class, ideFrameFixture.robot(), target);
    myIdeFrameFixture = ideFrameFixture;
  }

  public IdeFrameFixture performAction(@NotNull final String label) {
    checkState(target().isShowing(), "cannot click link on hidden notification");
    HyperlinkLabel link = robot().finder().find(target(), new GenericTypeMatcher<HyperlinkLabel>(HyperlinkLabel.class) {
      @Override
      protected boolean isMatching(@NotNull HyperlinkLabel hyperlinkLabel) {
        // IntelliJ's HyperLinkLabel class does not expose the getText method (it is package private)
        return hyperlinkLabel.isShowing() &&
               label.equals(Reflection.method("getText").withReturnType(String.class).in(hyperlinkLabel).invoke());
      }
    });
    driver().click(link);
    Wait.seconds(5).expecting("notification panel to disappear").until(() -> !target().isShowing());
    return myIdeFrameFixture;
  }
}
